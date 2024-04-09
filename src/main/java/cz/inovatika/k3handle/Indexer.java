package cz.inovatika.k3handle;

import com.qbizm.kramerius.imp.jaxb.DatastreamType;
import com.qbizm.kramerius.imp.jaxb.DigitalObject;
import cz.incad.kramerius.fedora.om.RepositoryException;
import cz.incad.kramerius.fedora.om.impl.AkubraUtils;
import cz.incad.kramerius.utils.FedoraUtils;
import cz.incad.kramerius.utils.XMLUtils;
import cz.incad.kramerius.utils.conf.KConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Indexer {

    static Logger LOGGER = Logger.getLogger(Indexer.class.getName());
    static int LOG_MESSAGE_ITERATION = 100;
    static String objectPaths = KConfiguration.getInstance().getProperty("objectStore.path");
    static String indexDir = KConfiguration.getInstance().getProperty("k3handle.indexDir", "${sys:user.home}/.kramerius4/k3handle");

    static Directory index;

    static {
        try {
            index = FSDirectory.open(Paths.get(indexDir));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isEmpty() {
        Path path = Path.of(indexDir);
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                return !stream.iterator().hasNext(); // Returns true if the directory is empty
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error checking index directory: " + path, e);
            }
        }
        return false; // Not a directory or doesn't exist
    }


    static void index() {
        IndexWriterConfig config = new IndexWriterConfig(); // No analyzer needed for StringField
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        Path objectStoreRoot = Paths.get(objectPaths);
        try (IndexWriter indexWriter = new IndexWriter(index, config)) {
            final AtomicInteger currentIteration = new AtomicInteger(0);
            Files.walk(objectStoreRoot).parallel().filter(Files::isRegularFile).forEach(path -> {
                try {
                    if ((currentIteration.incrementAndGet() % LOG_MESSAGE_ITERATION) == 0) {
                        LOGGER.info("Indexed " + currentIteration + " items.");
                    }
                    FileInputStream inputStream = new FileInputStream(path.toFile());
                    DigitalObject digitalObject = createDigitalObject(inputStream);
                    rebuildProcessingIndex(indexWriter, digitalObject);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error processing file: " + path, ex);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static DigitalObject createDigitalObject(InputStream inputStream) throws JAXBException {
        synchronized (unmarshaller) {
            return (DigitalObject) unmarshaller.unmarshal(inputStream);
        }
    }

    private static Unmarshaller unmarshaller = null;

    static {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DigitalObject.class);
            unmarshaller = jaxbContext.createUnmarshaller();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot init JAXB", e);
            throw new RuntimeException(e);
        }
    }

    private static void rebuildProcessingIndex(IndexWriter indexWriter, DigitalObject digitalObject) throws RepositoryException {
        try {
            List<DatastreamType> datastreamList = digitalObject.getDatastream();
            for (DatastreamType datastreamType : datastreamList) {
                if (FedoraUtils.RELS_EXT_STREAM.equals(datastreamType.getID())) {
                    InputStream streamContent = AkubraUtils.getStreamContent(AkubraUtils.getLastStreamVersion(datastreamType), null);
                    String relsExt = IOUtils.toString(streamContent, "UTF-8");
                    String handle = getHandleFromRelsExt(relsExt);
                    if (handle != null) {
                        String pid = digitalObject.getPID();
                        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                        doc.add(new StringField("handle", handle, Field.Store.YES));
                        doc.add(new StringField("pid", pid, Field.Store.YES));
                        indexWriter.addDocument(doc);
                    }
                }
            }
        } catch (Exception e) {
            throw new RepositoryException(e);
        }

    }

    private static String getHandleFromRelsExt(String relsExt) throws IOException, SAXException, ParserConfigurationException, RepositoryException {
        Document document = XMLUtils.parseDocument(new StringReader(relsExt), true);
        Element handle = XMLUtils.findElement(document.getDocumentElement(), "handle", "http://www.nsdl.org/ontologies/relationships#");
        return handle != null ? handle.getTextContent() : null;
    }

}
