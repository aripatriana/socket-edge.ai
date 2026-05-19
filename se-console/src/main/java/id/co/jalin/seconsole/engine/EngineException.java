package id.co.jalin.seconsole.engine;

public class EngineException extends RuntimeException {
    public EngineException(String msg) { super(msg); }
    public EngineException(String msg, Throwable cause) { super(msg, cause); }
}