package BankManagementSystem;

// ============================================================
//         BANK MANAGEMENT SYSTEM — OOP Project
//         A single-file Java implementation covering:
//         • Classes & Objects         • Static data/methods
//         • Constructors              • Arrays & ArrayLists
//         • Inheritance               • Data/Object Casting
//         • Encapsulation             • Polymorphism + Abstract
//         • Interfaces                • Nested / Inner Classes
//         • Generics                  • Exception Handling
//         • Multithreading  ← UNIQUE SELF-STUDIED TOPIC
//           (Session Timer Thread + Password System)
// ============================================================

import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean; // thread-safe boolean flag
import java.util.concurrent.locks.ReentrantLock;


// ============================================================
// SECTION 1 — CUSTOM EXCEPTIONS
// ============================================================

// Thrown when a withdrawal/transfer exceeds available balance
class InsufficientFundsException extends RuntimeException {
    private double amount;
    public InsufficientFundsException(double amount) {
        super("Insufficient funds. Attempted: Rs. " + amount);
        this.amount = amount;
    }
    public double getAmount() { return amount; }
}

// Thrown when account number not found
class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accNo) {
        super("Account not found: " + accNo);
    }
}

// Thrown when amount is negative or zero
class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String msg) { super(msg); }
}

// *** NEW *** Thrown when password is wrong OR session timer expired
class AuthException extends RuntimeException {
    public AuthException(String msg) { super(msg); }
}


// ============================================================
// SECTION 2 — INTERFACES
// ============================================================

interface Transferable {
    void transfer(Account target, double amount);
}

interface Printable {
    void printStatement();
}


// ============================================================
// SECTION 3 — GENERIC CLASS
// ============================================================

class TransactionLog<T> {
    private ArrayList<T> logs = new ArrayList<>();
    public void add(T entry)            { logs.add(entry); }
    public ArrayList<T> getAll()        { return logs; }
    public int size()                   { return logs.size(); }
}


// ============================================================
// SECTION 4 — SESSION TIMER (MULTITHREADING — UNIQUE TOPIC)
//
// This is where threading is demonstrated in a REAL, practical way.
//
// How it works:
//   1. When a user starts a transaction (deposit/withdraw),
//      a SessionTimer thread is launched in the background.
//   2. The timer counts down from 30 seconds.
//   3. If the user does NOT confirm the password before the
//      timer reaches zero, sessionExpired is set to true.
//   4. The main thread checks this flag — if expired, it throws
//      AuthException and cancels the operation.
//
// KEY THREADING CONCEPTS SHOWN:
//   • implements Runnable          — the standard way to create threads
//   • new Thread(runnable).start() — launching a background thread
//   • AtomicBoolean                — thread-safe flag shared between threads
//   • volatile keyword             — ensures both threads see latest value
//   • thread.interrupt()           — stopping the timer thread cleanly
//   • Two threads running at once  — timer thread + main thread simultaneously
// ============================================================

class SessionTimer implements Runnable {

    // How long the user has to enter their password (in seconds)
    public static final int TIMEOUT_SECONDS = 30;

    // AtomicBoolean is thread-safe — reading/writing from two threads is safe.
    // Regular boolean is NOT thread-safe (could give stale values).
    private AtomicBoolean sessionExpired;

    // volatile: guarantees the timer thread always reads the LATEST value
    // of this flag, not a cached copy sitting in CPU registers.
    private volatile boolean cancelled = false;

    // The Thread object — stored so we can interrupt it later
    private Thread timerThread;

    public SessionTimer(AtomicBoolean sessionExpired) {
        this.sessionExpired = sessionExpired;
    }

    // start() creates a new OS thread and calls run() inside it.
    // This runs PARALLEL to the main thread (user is typing password
    // while this thread is counting down simultaneously).
    public void start() {
        timerThread = new Thread(this, "SessionTimerThread");
        timerThread.setDaemon(true); // daemon = auto-killed when main program exits
        timerThread.start();
    }

    @Override
    public void run() {
        // This entire method runs in a SEPARATE thread from main()
        try {
            for (int i = TIMEOUT_SECONDS; i > 0; i--) {
                if (cancelled) return; // stop if transaction was completed

                // Show countdown only at key moments so it's visible
                if (i == TIMEOUT_SECONDS || i == 20 || i == 10 || i <= 5) {
                    System.out.println("\n  ⏱  Session expires in " + i + " seconds...");
                }

                Thread.sleep(1000); // sleep 1 second — this thread pauses, main continues
            }

            // Timer reached zero — set the shared flag
            if (!cancelled) {
                sessionExpired.set(true); // AtomicBoolean.set() is thread-safe
                System.out.println("\n\n  ⛔ Session expired! Transaction cancelled.");
                System.out.println("  You must re-enter your password to try again.\n");
            }

        } catch (InterruptedException e) {
            // Thread was interrupted — this is the clean shutdown signal
            // InterruptedException is thrown when another thread calls
            // timerThread.interrupt() — we just exit silently.
        }
    }

    // Called by main thread when user successfully enters password
    // Stops the countdown so it doesn't expire after success
    public void cancel() {
        cancelled = true;
        if (timerThread != null) {
            timerThread.interrupt(); // sends interrupt signal to the timer thread
        }
    }

    public boolean isExpired() {
        return sessionExpired.get(); // AtomicBoolean.get() is thread-safe
    }
}


// ============================================================
// SECTION 5 — ABSTRACT BASE CLASS (Account)
// ============================================================

abstract class Account implements Transferable, Printable {

    // Static fields — belong to the CLASS, shared by all Account objects
    private static String bankName      = "OOP National Bank";
    private static int    accountCounter = 0;

    // Instance fields — private (Encapsulation)
    private String accountNumber;
    private String holderName;
    private double balance;

    // *** NEW *** Password stored in account (private — Encapsulation)
    // In a real system this would be hashed (e.g. BCrypt), but for
    // this project we store it as a plain string for simplicity.
    private String password;

    // ── Inner Class (Nested/Inner Classes topic) ──────────────────────
    // Transaction is INSIDE Account — it logically belongs here.
    public class Transaction {
        private String type;
        private double amount;
        private double balanceAfter;

        public Transaction(String type, double amount, double balanceAfter) {
            this.type        = type;
            this.amount      = amount;
            this.balanceAfter = balanceAfter;
        }

        @Override
        public String toString() {
            return String.format("  %-12s | Rs. %10.2f | Balance: Rs. %10.2f",
                    type, amount, balanceAfter);
        }
    }

    // Generic log — uses our TransactionLog<T> class with Transaction as T
    private TransactionLog<Transaction> log = new TransactionLog<Transaction>();

    // ── Constructor ───────────────────────────────────────────────────
    public Account(String holderName, double initialDeposit, String password) {
        accountCounter++;
        this.accountNumber = "ACC" + String.format("%04d", accountCounter);
        this.holderName    = holderName;
        this.balance       = initialDeposit;
        this.password      = password; // *** NEW — store password

        log.add(new Transaction("OPEN", initialDeposit, balance));
        System.out.println("  ✓ Account created: " + accountNumber + " for " + holderName);
    }

    // ── Abstract methods — subclasses MUST implement these ────────────
    public abstract double calculateInterest();
    public abstract String getAccountType();

    // ── Static methods ────────────────────────────────────────────────
    public static String getBankName()      { return bankName; }
    public static int    getTotalAccounts() { return accountCounter; }

    // ── Getters (Encapsulation) ───────────────────────────────────────
    public String getAccountNumber() { return accountNumber; }
    public String getHolderName()    { return holderName; }
    public double getBalance()       { return balance; }

    // ── Password verification (Encapsulation — password field stays private) ──
    // Returns true if entered password matches stored password.
    // The password field itself is never exposed outside this class.
    public boolean verifyPassword(String entered) {
        return this.password.equals(entered);
    }

    // ── Core banking operations ───────────────────────────────────────

    public void deposit(double amount) {
        if (amount <= 0) throw new InvalidAmountException("Deposit must be positive.");
        balance += amount;
        log.add(new Transaction("DEPOSIT", amount, balance));
        System.out.println("  ✓ Deposited Rs. " + amount + " | New Balance: Rs. " + balance);
    }

    public void withdraw(double amount) {
        if (amount <= 0) throw new InvalidAmountException("Withdrawal must be positive.");
        if (amount > balance) throw new InsufficientFundsException(amount);
        balance -= amount;
        log.add(new Transaction("WITHDRAW", amount, balance));
        System.out.println("  ✓ Withdrew Rs. " + amount + " | New Balance: Rs. " + balance);
    }

    @Override
    public void transfer(Account target, double amount) {
        System.out.println("  Transferring Rs. " + amount
                + " → " + target.getAccountNumber());
        this.withdraw(amount);
        target.deposit(amount);
        log.add(new Transaction("TRANSFER-OUT", amount, balance));
    }

    @Override
    public void printStatement() {
        System.out.println("\n  ══════════════════════════════════════════════════");
        System.out.println("  Account  : " + accountNumber + " (" + getAccountType() + ")");
        System.out.println("  Holder   : " + holderName);
        System.out.println("  Balance  : Rs. " + balance);
        System.out.println("  ──────────────────────────────────────────────────");
        System.out.println("  Transactions (" + log.size() + "):");
        for (Transaction t : log.getAll()) {
            System.out.println(t);
        }
        System.out.println("  ══════════════════════════════════════════════════");
    }

    @Override
    public String toString() {
        return String.format("  [%s] %-20s | %-10s | Rs. %.2f",
                accountNumber, holderName, getAccountType(), balance);
    }
}


// ============================================================
// SECTION 6 — SAVINGS ACCOUNT (Inheritance)
// ============================================================

class SavingsAccount extends Account {
    private double interestRate;

    public SavingsAccount(String holderName, double initialDeposit,
                          double interestRate, String password) {
        super(holderName, initialDeposit, password); // calls Account constructor
        this.interestRate = interestRate;
    }

    @Override
    public double calculateInterest() {
        return getBalance() * (interestRate / 100); // simple annual interest
    }

    @Override
    public String getAccountType() { return "Savings"; }
}


// ============================================================
// SECTION 7 — CURRENT ACCOUNT (Inheritance)
// ============================================================

class CurrentAccount extends Account {
    private double overdraftLimit;

    public CurrentAccount(String holderName, double initialDeposit,
                          double overdraftLimit, String password) {
        super(holderName, initialDeposit, password);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    public void withdraw(double amount) {
        if (amount <= 0) throw new InvalidAmountException("Withdrawal must be positive.");
        if (amount > getBalance() + overdraftLimit) throw new InsufficientFundsException(amount);
        // Allow overdraft by temporarily extending balance
        super.deposit(overdraftLimit);
        super.withdraw(amount);
        super.withdraw(overdraftLimit);
    }

    @Override
    public double calculateInterest() { return 0.0; }

    @Override
    public String getAccountType() { return "Current"; }
}


// ============================================================
// SECTION 8 — BANK CLASS
// ============================================================

class Bank {
    private ArrayList<Account> accounts = new ArrayList<>();

    public void addAccount(Account acc) { accounts.add(acc); }

    public Account findAccount(String accNo) {
        for (Account acc : accounts) {
            if (acc.getAccountNumber().equals(accNo)) return acc;
        }
        throw new AccountNotFoundException(accNo);
    }

    public void listAccounts() {
        System.out.println("\n  ── Accounts at " + Account.getBankName() + " ──");
        if (accounts.isEmpty()) { System.out.println("  No accounts."); return; }
        for (Account acc : accounts) System.out.println(acc);
        System.out.println("  Total: " + Account.getTotalAccounts());
    }

    // Object casting demonstrated: upcast list → downcast to SavingsAccount
    public void applyInterestToSavings() {
        System.out.println("\n  ── Applying Interest ──");
        for (Account acc : accounts) {
            if (acc instanceof SavingsAccount) {                   // check type
                SavingsAccount sa = (SavingsAccount) acc;          // downcast
                double interest = sa.calculateInterest();
                sa.deposit(interest);
                System.out.println("  Interest Rs. " + interest + " → " + sa.getAccountNumber());
            }
        }
    }
}


// ============================================================
// SECTION 9 — PASSWORD + TIMER AUTHENTICATION HELPER
//
// This class handles the full "authenticate with timer" flow:
//
//  1. Starts the SessionTimer thread in the background
//  2. Prompts the user to enter their password
//  3. If the timer expires BEFORE password is entered → AuthException
//  4. If the password is wrong → AuthException
//  5. If password is correct → cancels the timer and returns true
//
// This is the clearest demonstration of threading:
//   • Timer thread runs concurrently while user types
//   • Main thread waits for input (Scanner.nextLine blocks it)
//   • Timer thread independently sets sessionExpired = true
//   • Main thread reads this shared flag after getting input
// ============================================================

class AuthHelper {

    // Shared atomic flag between main thread and timer thread.
    // AtomicBoolean guarantees that reads/writes are visible
    // to both threads immediately — no caching issues.
    private static AtomicBoolean sessionExpired = new AtomicBoolean(false);

    /**
     * Authenticates a user for a sensitive operation.
     *
     * Steps:
     *  1. Reset the shared expiry flag to false
     *  2. Start the countdown timer thread
     *  3. Ask user for password (blocks main thread on Scanner)
     *  4. After input — check if timer already expired
     *  5. Verify the password against the account
     *  6. Cancel the timer thread on success
     *
     * @param account  the account being accessed
     * @param sc       scanner for reading user input
     * @param action   description of the action (e.g. "DEPOSIT")
     */
    public static void authenticate(Account account, Scanner sc, String action) {

        // Reset flag — must be false at the start of each new transaction
        sessionExpired.set(false);

        // Create and start the timer thread — it runs in the background
        // from this point forward, counting down simultaneously
        SessionTimer timer = new SessionTimer(sessionExpired);
        timer.start();

        System.out.println("\n  ─── Secure " + action + " ───────────────────────────");
        System.out.println("  Account: " + account.getAccountNumber()
                + " | Holder: " + account.getHolderName());
        System.out.println("  You have " + SessionTimer.TIMEOUT_SECONDS
                + " seconds to enter your password.");
        System.out.print("  Password: ");

        // Scanner.nextLine() BLOCKS the main thread here.
        // The timer thread continues running independently in the background.
        // This is the key moment where two threads are active simultaneously.
        String entered = sc.nextLine();

        // ── After user presses Enter — check if timer already fired ──
        if (timer.isExpired()) {
            // Timer thread already set the flag — session is dead
            throw new AuthException("Session expired. Transaction cancelled.");
        }

        // ── Timer is still running — now verify the password ──
        if (!account.verifyPassword(entered)) {
            timer.cancel(); // stop the countdown (no point continuing)
            throw new AuthException("Wrong password! Transaction denied.");
        }

        // ── Success — stop the timer thread immediately ──
        timer.cancel();
        System.out.println("  ✓ Authenticated! Processing " + action + "...\n");
    }
}


// ============================================================
// SECTION 10 — MAIN CLASS
// ============================================================

public class BankManagementSystem {

    public static void main(String[] args) {

        Scanner sc      = new Scanner(System.in);
        Bank    bank    = new Bank();
        boolean running = true;

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║          " + Account.getBankName() + "               ║");
        System.out.println("║            Bank Management System v2.0               ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println("  Threading demo: deposit/withdraw require password.");
        System.out.println("  You have " + SessionTimer.TIMEOUT_SECONDS + "s to enter it or the session expires!\n");

        while (running) {

            System.out.println("\n══════════════ MAIN MENU ══════════════");
            System.out.println("  1. Create Savings Account");
            System.out.println("  2. Create Current Account");
            System.out.println("  3. Deposit      ← password + timer");
            System.out.println("  4. Withdraw     ← password + timer");
            System.out.println("  5. Transfer     ← password + timer");
            System.out.println("  6. View Statement");
            System.out.println("  7. List All Accounts");
            System.out.println("  8. Apply Interest (Savings)");
            System.out.println("  0. Exit");
            System.out.print("  Choose: ");

            int choice;
            try {
                choice = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a number.");
                continue;
            }

            try {
                switch (choice) {

                    // ── CREATE SAVINGS ACCOUNT ────────────────────────────
                    case 1: {
                        System.out.print("  Holder name: ");
                        String name = sc.nextLine();
                        System.out.print("  Initial deposit (Rs.): ");
                        double deposit = Double.parseDouble(sc.nextLine());
                        System.out.print("  Annual interest rate (%): ");
                        double rate = Double.parseDouble(sc.nextLine());

                        // *** NEW — set password at account creation
                        System.out.print("  Set password for this account: ");
                        String pass = sc.nextLine();
                        System.out.print("  Confirm password: ");
                        String confirm = sc.nextLine();
                        if (!pass.equals(confirm)) {
                            System.out.println("  Passwords do not match. Account not created.");
                            break;
                        }

                        // Polymorphism: stored as Account reference
                        Account acc = new SavingsAccount(name, deposit, rate, pass);
                        bank.addAccount(acc);
                        break;
                    }

                    // ── CREATE CURRENT ACCOUNT ────────────────────────────
                    case 2: {
                        System.out.print("  Holder name: ");
                        String name = sc.nextLine();
                        System.out.print("  Initial deposit (Rs.): ");
                        double deposit = Double.parseDouble(sc.nextLine());
                        System.out.print("  Overdraft limit (Rs.): ");
                        double overdraft = Double.parseDouble(sc.nextLine());

                        // *** NEW — set password
                        System.out.print("  Set password for this account: ");
                        String pass = sc.nextLine();
                        System.out.print("  Confirm password: ");
                        String confirm = sc.nextLine();
                        if (!pass.equals(confirm)) {
                            System.out.println("  Passwords do not match. Account not created.");
                            break;
                        }

                        Account acc = new CurrentAccount(name, deposit, overdraft, pass);
                        bank.addAccount(acc);
                        break;
                    }

                    // ── DEPOSIT ← PASSWORD + TIMER REQUIRED ──────────────
                    case 3: {
                        System.out.print("  Account number: ");
                        String accNo = sc.nextLine().toUpperCase();
                        Account acc = bank.findAccount(accNo);

                        // *** KEY THREADING MOMENT ***
                        // authenticate() starts the timer thread THEN waits
                        // for password input. Both threads run concurrently.
                        // If timer fires first → AuthException
                        // If wrong password  → AuthException
                        // If correct in time → timer cancelled, proceed
                        AuthHelper.authenticate(acc, sc, "DEPOSIT");

                        System.out.print("  Amount to deposit (Rs.): ");
                        double amount = Double.parseDouble(sc.nextLine());
                        acc.deposit(amount);
                        break;
                    }

                    // ── WITHDRAW ← PASSWORD + TIMER REQUIRED ─────────────
                    case 4: {
                        System.out.print("  Account number: ");
                        String accNo = sc.nextLine().toUpperCase();
                        Account acc = bank.findAccount(accNo);

                        // Same pattern — timer + password before any money moves
                        AuthHelper.authenticate(acc, sc, "WITHDRAW");

                        System.out.print("  Amount to withdraw (Rs.): ");
                        double amount = Double.parseDouble(sc.nextLine());
                        acc.withdraw(amount);
                        break;
                    }

                    // ── TRANSFER ← PASSWORD + TIMER REQUIRED ─────────────
                    case 5: {
                        System.out.print("  Source account: ");
                        String from = sc.nextLine().toUpperCase();
                        System.out.print("  Target account: ");
                        String to   = sc.nextLine().toUpperCase();
                        Account srcAcc = bank.findAccount(from);
                        Account tgtAcc = bank.findAccount(to);

                        // Authenticate on the SOURCE account (the one losing money)
                        AuthHelper.authenticate(srcAcc, sc, "TRANSFER");

                        System.out.print("  Amount to transfer (Rs.): ");
                        double amount = Double.parseDouble(sc.nextLine());
                        srcAcc.transfer(tgtAcc, amount);
                        break;
                    }

                    // ── VIEW STATEMENT ────────────────────────────────────
                    case 6: {
                        System.out.print("  Account number: ");
                        String accNo = sc.nextLine().toUpperCase();
                        bank.findAccount(accNo).printStatement(); // Printable interface
                        break;
                    }

                    // ── LIST ACCOUNTS ─────────────────────────────────────
                    case 7: {
                        bank.listAccounts();
                        break;
                    }

                    // ── APPLY INTEREST ────────────────────────────────────
                    case 8: {
                        bank.applyInterestToSavings(); // uses instanceof + downcast
                        break;
                    }

                    case 0:
                        running = false;
                        System.out.println("\n  Thank you for using " + Account.getBankName() + "!");
                        break;

                    default:
                        System.out.println("  Invalid option. Try again.");
                }

                // ── EXCEPTION HANDLING ─────────────────────────────────────
            } catch (AuthException e) {
                // Wrong password OR session timer expired
                System.out.println("\n  🔒 AUTH FAILED: " + e.getMessage());
            } catch (AccountNotFoundException e) {
                System.out.println("\n  ERROR: " + e.getMessage());
            } catch (InsufficientFundsException e) {
                System.out.println("\n  ERROR: " + e.getMessage());
            } catch (InvalidAmountException e) {
                System.out.println("\n  ERROR: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("\n  ERROR: Please enter a valid number.");
            }
        }

        sc.close();
    }
}


// ============================================================
//  OOP CONCEPTS — WHERE EACH IS USED
// ============================================================
//
//  CONCEPT                  | WHERE USED
//  ─────────────────────────┼────────────────────────────────────────────
//  Classes & Objects        | Account, Bank, SessionTimer, AuthHelper, etc.
//  Constructors             | All classes; password passed in constructor
//  Static Data/Methods      | Account.bankName, accountCounter; TIMEOUT_SECONDS
//  Arrays & ArrayLists      | ArrayList<Account> in Bank
//  Inheritance              | SavingsAccount, CurrentAccount extend Account
//  Data/Object Casting      | Upcasting to Account; downcasting in applyInterest
//  Encapsulation            | password field private; verifyPassword() is the only access
//  Polymorphism + Abstract  | abstract calculateInterest() / getAccountType()
//  Interfaces               | Transferable → transfer(); Printable → printStatement()
//  Nested / Inner Classes   | Transaction defined inside Account
//  Generics                 | TransactionLog<T>
//  Exception Handling       | AuthException (NEW), InsufficientFundsException, etc.
//  Multithreading (UNIQUE)  | SessionTimer implements Runnable
//                           | AtomicBoolean shared between threads
//                           | Timer thread runs concurrently with main (password input)
//                           | thread.interrupt() for clean shutdown
//                           | Daemon thread (auto-killed on exit)
// ============================================================