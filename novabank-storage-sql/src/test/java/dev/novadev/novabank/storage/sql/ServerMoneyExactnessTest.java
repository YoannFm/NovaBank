package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Montants exacts sur un vrai serveur MySQL / MariaDB ({@code DECIMAL(38,4)}) : la plus petite
 * unite et un tres grand montant sont relus a l'identique, par le stockage comme en SQL direct.
 */
@RequiresSqlServer
class ServerMoneyExactnessTest {

    private static final ItemKey GOLD = ItemKey.minecraft("gold_block");

    private ServerDatabase database;
    private SqlStorage storage;

    @BeforeEach
    void open() throws SQLException {
        database = ServerDatabase.create();
        storage = database.open(SqlStorage.DEFAULT_TABLE_PREFIX);
    }

    @AfterEach
    void close() throws SQLException {
        storage.close();
        database.close();
    }

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "0.0001, 1, 0.0001",
            "123456789012345.6789, 1, 123456789012345.6789",
            "123456789012345.6789, 1000, 123456789012345678.9000",
            "0.3333, 3, 0.9999"})
    void un_montant_est_relu_exactement(String price, long quantity, String expected) throws SQLException {
        BankLedger ledger = new BankLedger(storage.banks(), new MutablePriceCatalog().price(GOLD, price),
                storage.deliveries(), UndeliveredPolicy.RETURN_TO_BANK, Clock.systemUTC());
        BankId bankId = TestBankIds.random();
        ledger.openBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);
        OperationId deposit = OperationId.random();

        assertThat(ledger.deposit(deposit, bankId, GOLD, quantity).status()).isEqualTo(OperationStatus.SUCCESS);

        BigDecimal exact = new BigDecimal(expected).setScale(4);
        assertThat(storage.banks().findBank(bankId).orElseThrow().line(GOLD).accumulatedValue()).isEqualTo(exact);
        assertThat(storage.banks().findOperation(deposit).orElseThrow().valueDelta()).isEqualTo(exact);
        try (Connection connection = database.rawConnection();
             PreparedStatement select = connection.prepareStatement("SELECT accumulated_value FROM"
                     + " novabank_stock_lines WHERE bank_id = ?")) {
            select.setString(1, bankId.toString());
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo(exact.toPlainString());
            }
        }

        OperationId withdrawal = OperationId.random();
        assertThat(ledger.reserveWithdrawal(withdrawal, bankId, GOLD, quantity, UUID.randomUUID()).status())
                .isEqualTo(OperationStatus.SUCCESS);
        assertThat(storage.banks().findBank(bankId).orElseThrow().line(GOLD).accumulatedValue())
                .isEqualTo(BigDecimal.ZERO.setScale(4));
    }
}
