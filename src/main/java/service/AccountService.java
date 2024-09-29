package service;

import client.Client;
import client.LoginState;
import database.account.Account;
import database.account.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import net.server.Server;
import net.server.coordinator.session.SessionCoordinator;
import tools.BCrypt;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * @author Ponk
 */
@Slf4j
public class AccountService {
    private static final LocalDate GMS_RELEASE = LocalDate.of(2005, 5, 11);
    private static final byte INITIAL_CHR_SLOTS = 3;

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createNew(String name, String password) {
        Account newAccount = Account.builder()
                .name(name)
                .password(hashPassword(password))
                .birthdate(GMS_RELEASE)
                .chrSlots(INITIAL_CHR_SLOTS)
                .loginState(LoginState.NOT_LOGGED_IN)
                .gender(null)
                .build();

        Integer accountId;
        try {
            accountId = accountRepository.insert(newAccount);
        } catch (Exception e) {
            log.error("Failed to insert new account", e);
            throw new RuntimeException("Failed to insert new account");
        }

        return getAccount(accountId)
                .orElseThrow(() -> new RuntimeException("Failed to get account after insert, id: " + accountId));
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public Optional<Account> getAccount(String name) {
        return accountRepository.findByNameIgnoreCase(name);
    }

    public Optional<Account> getAccount(int accountId) {
        return accountRepository.findById(accountId);
    }

    public boolean acceptTos(int accountId) {
        acceptTosMysql(accountId);
        acceptTosPostgres(accountId);
        return true;
    }

    private boolean acceptTosMysql(int accountId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT `tos` FROM accounts WHERE id = ?")) {
                ps.setInt(1, accountId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (rs.getByte("tos") == 1) {
                            return false;
                        }
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement("UPDATE accounts SET tos = 1 WHERE id = ?")) {
                ps.setInt(1, accountId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean acceptTosPostgres(int accountId) {
        Optional<Account> account = getAccount(accountId);
        if (account.isEmpty()) {
            return false;
        }

        if (account.get().acceptedTos()) {
            return false;
        }

        accountRepository.setTos(accountId, true);
        return true;
    }

    public boolean setGender(int accountId, byte gender) {
        setGenderMysql(accountId, gender);
        return setGenderPostgres(accountId, gender);
    }

    private void setGenderMysql(int accountId, byte gender) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE accounts SET gender = ? WHERE id = ?")) {
            ps.setByte(1, gender);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean setGenderPostgres(int accountId, byte gender) {
        boolean success = accountRepository.setGender(accountId, gender);
        if (!success) {
            log.warn("Failed to set gender, account:{}, gender:{}", accountId, gender);
        }
        return success;
    }

    public void setPin(int accountId, String pin) {
        setPinMysql(accountId, pin);
        setPinPostgres(accountId, pin);
    }

    private void setPinMysql(int accountId, String pin) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE accounts SET pin = ? WHERE id = ?")) {
            ps.setString(1, pin);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setPinPostgres(int accountId, String pin) {
        try {
            boolean success = accountRepository.setPin(accountId, pin);
            if (!success) {
                log.warn("Failed to set pin (no updated rows) - account:{}, pin:{}", accountId, pin);
            }
        } catch (Exception e) {
            log.error("Failed to set pin due to error - account:{}, pin:{}", accountId, pin, e);
        }
    }

    public void setPic(int accountId, String pic) {
        setPicMysql(accountId, pic);
        setPicPostgres(accountId, pic);
    }

    private void setPicMysql(int accountId, String pic) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE accounts SET pic = ? WHERE id = ?")) {
            ps.setString(1, pic);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setPicPostgres(int accountId, String pic) {
        try {
            boolean success = accountRepository.setPic(accountId, pic);
            if (!success) {
                log.warn("Failed to set pic (no updated rows) - account:{}, pic:{}", accountId, pic);
            }
        } catch (Exception e) {
            log.error("Failed to set pic - account:{}, pin:{}", accountId, pic, e);
        }
    }

    public boolean addChrSlot(Client c) {
        if (!c.gainCharacterSlot()) {
            return false;
        }

        int newChrSlots = c.getCharacterSlots() + 1;
        setChrSlotsMysql(c.getAccID(), newChrSlots);
        return setChrSlotsPostgres(c.getAccID(), newChrSlots);
    }

    private void setChrSlotsMysql(int accountId, int chrSlots) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE accounts SET characterslots = ? WHERE id = ?")) {
            ps.setInt(1, chrSlots);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean setChrSlotsPostgres(int accountId, int chrSlots) {
        return accountRepository.setChrSlots(accountId, chrSlots);
    }

    public boolean logIn(Client c) {
        byte newState = LoginState.LOGGED_IN;
        int currentState = c.getLoginState();
        if (currentState != LoginState.NOT_LOGGED_IN && currentState != LoginState.SERVER_TRANSITION) {
            return false;
        }

        setLoginStateMysql(c.getAccID(), newState);
        setLoginStatePostgres(c.getAccID(), newState);
        c.setLoginState(newState);
        return true;
    }

    public void logOut(Client c) {
        SessionCoordinator.getInstance().closeSession(c, false);
        byte newState = LoginState.NOT_LOGGED_IN;
        int accountId = c.getAccID();
        setLoginStateMysql(accountId, newState);
        setLoginStatePostgres(accountId, newState);
        c.setLoginState(newState);
    }

    private void setLoginStateMysql(int accountId, byte newState) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE accounts SET loggedin = ?, lastlogin = ? WHERE id = ?")) {
            // using sql currenttime here could potentially break the login, thanks Arnah for pointing this out

            ps.setInt(1, newState);
            ps.setTimestamp(2, new java.sql.Timestamp(Server.getInstance().getCurrentTime()));
            ps.setInt(3, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setLoginStatePostgres(int accountId, byte newState) {
        Instant loginTime = Instant.now();
        boolean success = accountRepository.setLoginState(accountId, newState, loginTime);
        if (!success) {
            log.warn("Failed to set login state - account:{}, newState:{}, loginTime:{}", accountId, newState, loginTime);
        }
    }

}
