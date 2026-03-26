package org.example.bankrest.exception;

public class CardOperationException extends RuntimeException {
    public CardOperationException(String message) {
        super(message);
    }
}
