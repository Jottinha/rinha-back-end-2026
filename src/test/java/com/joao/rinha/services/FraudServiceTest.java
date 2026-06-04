package com.joao.rinha.services;

import com.joao.rinha.configs.NormalizationConfig;
import com.joao.rinha.dto.PaymentDTO;
import com.joao.rinha.pojo.Customer;
import com.joao.rinha.pojo.LastTransaction;
import com.joao.rinha.pojo.Merchant;
import com.joao.rinha.pojo.Terminal;
import com.joao.rinha.pojo.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes do {@link FraudService#transformInVector}.
 *
 * As regras de normalizacao seguem a especificacao oficial da Rinha de Backend 2026:
 *   - DATASET.md
 *   - REGRAS_DE_DETECCAO.md
 *
 * Divisores oficiais usados na vetorizacao (14 dimensoes):
 *   amount            / 10000
 *   installments      / 12
 *   (amount/avg)      / 10
 *   hora UTC          / 23
 *   dia da semana     / 6      (segunda = 0 ... domingo = 6)
 *   minutos desde ult.tx / 1440  (ou -1 se nao houver last_transaction)
 *   km desde ult.tx   / 1000     (ou -1 se nao houver last_transaction)
 *   km de casa        / 1000
 *   tx_count_24h      / 20
 *   is_online         -> 1 / 0
 *   card_present      -> 1 / 0
 *   merchant desconhecido -> 1, conhecido -> 0
 *   risco do MCC      -> lookup (padrao 0.5)
 *   merchant avg      / 10000
 */
class FraudServiceTest {

    private static final float DELTA = 1e-4f;

    private NormalizationDataLoader normalizationLoader;
    private MccRiskDataLoader mccRiskLoader;
    private ReferenceVectorDataLoader vectorDataLoader;
    private FraudService fraudService;

    @BeforeEach
    void setUp() {
        normalizationLoader = Mockito.mock(NormalizationDataLoader.class);
        mccRiskLoader = Mockito.mock(MccRiskDataLoader.class);
        vectorDataLoader = Mockito.mock(ReferenceVectorDataLoader.class);

        // Divisores conforme a especificacao oficial (REGRAS_DE_DETECCAO.md).
        NormalizationConfig config = new NormalizationConfig();
        config.setMaxAmount(10000);
        config.setMaxInstallments(12);
        config.setAmountVsAvgRatio(10);
        config.setMaxMinutes(1440);
        config.setMaxKm(1000);
        config.setMaxTxCount24h(20);
        config.setMaxMerchantAvgAmount(10000);

        Mockito.when(normalizationLoader.getConfig()).thenReturn(config);
        Mockito.when(mccRiskLoader.getMccRiskMap()).thenReturn(Map.of("5912", 0.7));

        fraudService = new FraudService(normalizationLoader, mccRiskLoader, vectorDataLoader);
    }

    /**
     * DTO equivalente ao primeiro JSON de exemplo (tx-3576980410), com last_transaction preenchido.
     * requested_at = 2026-03-11T20:23:35Z  -> quarta-feira (dia 2), hora 20 UTC.
     * last_transaction.timestamp = 2026-03-11T14:58:35Z -> 325 minutos antes.
     */
    private PaymentDTO buildBaseDto() {
        Transaction transaction = new Transaction();
        transaction.setAmount(384.88f);
        transaction.setInstallments(3);
        transaction.setRequested_at(Instant.parse("2026-03-11T20:23:35Z"));

        Customer customer = new Customer();
        customer.setAvg_amount(769.76f);
        customer.setTx_count_24h(3);
        customer.setKnown_merchants(new String[]{"MERC-009", "MERC-001", "MERC-001"});

        Merchant merchant = new Merchant();
        merchant.setId("MERC-001");
        merchant.setMcc("5912");
        merchant.setAvg_amount(298.95f);

        Terminal terminal = new Terminal();
        terminal.setIs_online(false);
        terminal.setCard_present(true);
        terminal.setKm_from_home(13.7090520965f);

        LastTransaction lastTransaction = new LastTransaction();
        lastTransaction.setTimestamp(Instant.parse("2026-03-11T14:58:35Z"));
        lastTransaction.setKm_from_current(18.8626479774f);

        PaymentDTO dto = new PaymentDTO();
        dto.setId("tx-3576980410");
        dto.setTransaction(transaction);
        dto.setCustomer(customer);
        dto.setMerchant(merchant);
        dto.setTerminal(terminal);
        dto.setLast_transaction(lastTransaction);
        return dto;
    }

    /**
     * DTO equivalente ao segundo JSON de exemplo (tx-1329056812), com last_transaction nulo.
     * requested_at = 2026-03-11T18:45:53Z -> quarta-feira (dia 2), hora 18 UTC.
     */
    private PaymentDTO buildDtoSemLastTransaction() {
        Transaction transaction = new Transaction();
        transaction.setAmount(41.12f);
        transaction.setInstallments(2);
        transaction.setRequested_at(Instant.parse("2026-03-11T18:45:53Z"));

        Customer customer = new Customer();
        customer.setAvg_amount(82.24f);
        customer.setTx_count_24h(3);
        customer.setKnown_merchants(new String[]{"MERC-003", "MERC-016"});

        Merchant merchant = new Merchant();
        merchant.setId("MERC-016");
        merchant.setMcc("5411");
        merchant.setAvg_amount(60.25f);

        Terminal terminal = new Terminal();
        terminal.setIs_online(false);
        terminal.setCard_present(true);
        terminal.setKm_from_home(29.23f);

        PaymentDTO dto = new PaymentDTO();
        dto.setId("tx-1329056812");
        dto.setTransaction(transaction);
        dto.setCustomer(customer);
        dto.setMerchant(merchant);
        dto.setTerminal(terminal);
        dto.setLast_transaction(null);
        return dto;
    }

    @Test
    void transformInVector_comLastTransactionPreenchido_retornaVetorCompleto() {
        PaymentDTO dto = buildBaseDto();

        float[] vector = fraudService.transformInVector(dto);

        assertEquals(14, vector.length);
        assertEquals(384.88f / 10000, vector[0], DELTA);                 // amount / max_amount
        assertEquals(3f / 12, vector[1], DELTA);                         // installments / max_installments
        assertEquals((384.88f / 769.76f) / 10, vector[2], DELTA);        // (amount/avg) / ratio
        assertEquals(20 / 23.0f, vector[3], DELTA);                      // hora UTC (20) / 23
        assertEquals(2 / 6.0f, vector[4], DELTA);                        // quarta-feira (2) / 6
        assertEquals(325f / 1440, vector[5], DELTA);                     // minutos desde ult.tx (325) / 1440
        assertEquals(18.8626479774f / 1000, vector[6], DELTA);           // km desde ult.tx / 1000
        assertEquals(13.7090520965f / 1000, vector[7], DELTA);           // km de casa / 1000
        assertEquals(3f / 20, vector[8], DELTA);                         // tx_count_24h / 20
        assertEquals(0f, vector[9], DELTA);                              // is_online = false
        assertEquals(1f, vector[10], DELTA);                            // card_present = true
        assertEquals(0f, vector[11], DELTA);                            // merchant conhecido -> 0
        assertEquals(0.7f, vector[12], DELTA);                          // risco do mcc 5912
        assertEquals(298.95f / 10000, vector[13], DELTA);              // merchant avg / max
    }

    @Test
    void transformInVector_quandoLastTransactionPreenchido_calculaPosicoes5e6() {
        PaymentDTO dto = buildBaseDto();

        float[] vector = fraudService.transformInVector(dto);

        // Posicao 5: minutos entre last_transaction.timestamp e transaction.requested_at, normalizado por 1440.
        assertEquals(325f / 1440, vector[5], DELTA);
        // Posicao 6: km_from_current normalizado por 1000.
        assertEquals(18.8626479774f / 1000, vector[6], DELTA);
    }

    @Test
    void transformInVector_quandoSemLastTransacao_posicoes5e6Valem_menos1() {
        PaymentDTO dto = buildDtoSemLastTransaction();

        float[] vector = fraudService.transformInVector(dto);

        // Sem historico: sentinela -1 para distinguir de uma normalizacao legitima proxima de zero.
        assertEquals(-1f, vector[5], DELTA);
        assertEquals(-1f, vector[6], DELTA);
    }

    @Test
    void transformInVector_comSegundoJson_eLastTransactionNulo_retornaVetorEsperado() {
        PaymentDTO dto = buildDtoSemLastTransaction();

        float[] vector = fraudService.transformInVector(dto);

        assertEquals(14, vector.length);
        assertEquals(41.12f / 10000, vector[0], DELTA);                 // amount / max_amount
        assertEquals(2f / 12, vector[1], DELTA);                        // installments / max_installments
        assertEquals((41.12f / 82.24f) / 10, vector[2], DELTA);         // (amount/avg) / ratio
        assertEquals(18 / 23.0f, vector[3], DELTA);                     // hora UTC (18) / 23
        assertEquals(2 / 6.0f, vector[4], DELTA);                       // quarta-feira (2) / 6
        assertEquals(-1f, vector[5], DELTA);                            // last_transaction nulo
        assertEquals(-1f, vector[6], DELTA);                            // last_transaction nulo
        assertEquals(29.23f / 1000, vector[7], DELTA);                  // km de casa / 1000
        assertEquals(3f / 20, vector[8], DELTA);                        // tx_count_24h / 20
        assertEquals(0f, vector[9], DELTA);                             // is_online = false
        assertEquals(1f, vector[10], DELTA);                           // card_present = true
        assertEquals(0f, vector[11], DELTA);                           // merchant MERC-016 conhecido -> 0
        assertEquals(0.5f, vector[12], DELTA);                         // mcc 5411 sem risco -> padrao 0.5
        assertEquals(60.25f / 10000, vector[13], DELTA);              // merchant avg / max
    }

    @Test
    void transformInVector_quandoMerchantDesconhecido_posicao11Vale1() {
        PaymentDTO dto = buildBaseDto();
        dto.getCustomer().setKnown_merchants(new String[]{"MERC-009", "MERC-002"});

        float[] vector = fraudService.transformInVector(dto);

        assertEquals(1f, vector[11], DELTA);
    }

    @Test
    void transformInVector_quandoMccSemRisco_usaValorPadrao05() {
        PaymentDTO dto = buildBaseDto();
        dto.getMerchant().setMcc("0000");

        float[] vector = fraudService.transformInVector(dto);

        assertEquals(0.5f, vector[12], DELTA);
    }

    @Test
    void transformInVector_quandoValoresExtremos_saoLimitadosEntreZeroEUm() {
        PaymentDTO dto = buildBaseDto();
        dto.getTransaction().setAmount(50000f);          // 50000/10000 = 5 -> limitado a 1
        dto.getTransaction().setInstallments(99);        // 99/12 -> limitado a 1
        dto.getCustomer().setTx_count_24h(200);          // 200/20 -> limitado a 1
        dto.getTerminal().setKm_from_home(5000f);        // 5000/1000 -> limitado a 1

        float[] vector = fraudService.transformInVector(dto);

        assertEquals(1f, vector[0], DELTA);
        assertEquals(1f, vector[1], DELTA);
        assertEquals(1f, vector[7], DELTA);
        assertEquals(1f, vector[8], DELTA);
    }

    @Test
    void limit_quandoValorMaiorQueUm_retornaUm() {
        assertEquals(1.0f, fraudService.limit(1.5f));
    }

    @Test
    void limit_quandoValorMenorQueZero_retornaZero() {
        assertEquals(0.0f, fraudService.limit(-0.5f));
    }

    @Test
    void limit_quandoValorDentroDoIntervalo_retornaProprioValor() {
        float valor = 0.5f;
        assertEquals(valor, fraudService.limit(valor));
    }
}
