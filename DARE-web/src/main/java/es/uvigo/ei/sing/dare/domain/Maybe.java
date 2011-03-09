package es.uvigo.ei.sing.dare.domain;

public abstract class Maybe<T> {

    public static <T> Maybe<T> value(final T value) {
        return new Maybe<T>() {

            @Override
            public boolean hasValue() {
                return true;
            }

            @Override
            public T getValue() {
                return value;
            }
        };
    }

    public static <T> Maybe<T> none() {
        return new Maybe<T>() {

            @Override
            public boolean hasValue() {
                return false;
            }

            @Override
            public T getValue() {
                throw new UnsupportedOperationException(
                        "Maybe created with None");
            }
        };
    }

    public boolean isNone() {
        return !hasValue();
    }

    public abstract boolean hasValue();

    public abstract T getValue();

}
