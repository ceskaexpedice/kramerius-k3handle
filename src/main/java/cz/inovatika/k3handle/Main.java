package cz.inovatika.k3handle;

import io.javalin.Javalin;

public class Main {

    public static void main(String[] args) {
        if (Indexer.isEmpty()) {
            Indexer.index();
        }
        Javalin.create()
                .get("/*", Resolver::resolve)
                .start(8080);
    }
}
