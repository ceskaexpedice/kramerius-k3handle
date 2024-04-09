package cz.inovatika.k3handle;

import io.javalin.Javalin;

public class Main {

    public static void main(String[] args) {
        if (Indexer.isEmpty()) {
            Indexer.index();
        }
        Javalin.create()
                .get("/*", ctx -> ctx.redirect(Resolver.resolve(ctx)))
                .start(8080);
    }
}
