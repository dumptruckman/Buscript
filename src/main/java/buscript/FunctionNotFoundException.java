package buscript;

public class FunctionNotFoundException extends Exception {

    public FunctionNotFoundException() {
        this("");
    }

    public FunctionNotFoundException(String message) {
       super(message);
    }
}
