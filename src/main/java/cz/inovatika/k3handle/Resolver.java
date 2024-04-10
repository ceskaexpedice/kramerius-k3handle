package cz.inovatika.k3handle;

import cz.incad.kramerius.utils.conf.KConfiguration;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

import static cz.inovatika.k3handle.Indexer.index;

public class Resolver {

    static IndexReader reader;
    static IndexSearcher searcher;

    static {
        try {
            reader = DirectoryReader.open(index);
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void resolve(Context ctx) throws IOException {
        String fullPath = ctx.fullUrl();
        String handlePrefix = KConfiguration.getInstance().getConfiguration().getString("k3handle.handlePrefix", "handle/");
        int handleIndex = fullPath.lastIndexOf(handlePrefix);
        String handle = fullPath.substring(handleIndex + handlePrefix.length());
        Query query = new TermQuery(new Term("handle", new BytesRef(handle)));
        ScoreDoc[] hits = searcher.search(query, 1).scoreDocs;
        for (ScoreDoc hit : hits) {
            Document doc = searcher.storedFields().document(hit.doc);
            String base = KConfiguration.getInstance().getConfiguration().getString("k3handle.redirectBase", "https://k7.inovatika.dev/uuid/");
            ctx.redirect( base + doc.get("pid"));
            return;
        }
        ctx.status(HttpStatus.NOT_FOUND);
        ctx.result("Handle Not Found");
    }
}
