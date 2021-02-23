package den.tal.stream.watch.exceptions;

public class WatchException extends Exception {

    public WatchException(String message) {
        super(message);
    }

    public WatchException(Throwable ex) {
        super(ex);
    }
}
