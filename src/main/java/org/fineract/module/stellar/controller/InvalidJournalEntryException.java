package org.fineract.module.stellar.controller;

public class InvalidJournalEntryException extends RuntimeException {
  public InvalidJournalEntryException() { super("Invalid journal entry."); }
}
