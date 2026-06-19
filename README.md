Bank Management System

A console-based Bank Management System built in Java, demonstrating core Object-Oriented Programming principles alongside a multithreaded session-timeout feature for secure transactions.

Features

Create Savings and Current accounts
Deposit, Withdraw, and Transfer funds — each protected by password authentication with a live countdown timer
View account statement (transaction history)
List all accounts in the bank
Apply interest to savings accounts
Custom exception handling for invalid amounts, insufficient funds, missing accounts, and failed authentication


Multithreading (Unique Feature)

Every sensitive operation (deposit, withdraw, transfer) requires password entry within a time limit:
A SessionTimer thread (implements Runnable) runs concurrently with the main thread while the user types their password
An AtomicBoolean flag is shared safely between threads to track timeout state
The timer thread is interrupted cleanly (thread.interrupt()) once authentication succeeds
Implemented as a daemon thread, so it's automatically terminated on program exit

Authors

Built as a 3-member team project for the Object-Oriented Programming course at FAST-NUCES.
