package org.apache.streamline.registries.parser.service;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.io.ByteStreams;
import org.apache.streamline.common.QueryParam;
import org.apache.streamline.common.Schema;
import org.apache.streamline.common.util.FileStorage;
import org.apache.streamline.common.util.FileUtil;
import org.apache.streamline.common.util.ProxyUtil;
import org.apache.streamline.registries.parser.Parser;
import org.apache.streamline.registries.parser.ParserInfo;
import org.apache.streamline.storage.Storable;
import org.apache.streamline.storage.StorableKey;
import org.apache.streamline.storage.StorageManager;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * A service layer where we could put our business logic.
 * Right now this exists as a very thin layer between the DAO and
 * the REST controllers.
 */
public class ParsersCatalogService {
    private static final Logger LOG = LoggerFactory.getLogger(ParsersCatalogService.class);

    private static final String PARSER_INFO_NAMESPACE = new ParserInfo().getNameSpace();

    private final ProxyUtil<Parser> parserProxyUtil;
    private final StorageManager dao;
    private final FileStorage fileStorage;


    public ParsersCatalogService (StorageManager dao, FileStorage fileStorage) {
        this.dao = dao;
        dao.registerStorables(getStorableClasses());
        this.fileStorage = fileStorage;
        try {
            this.parserProxyUtil = new ProxyUtil<>(Parser.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Collection<Class<? extends Storable>> getStorableClasses() {
        InputStream resourceAsStream = ParsersCatalogService.class.getClassLoader().getResourceAsStream("parserregistrystorables.props");
        HashSet<Class<? extends Storable>> classes = new HashSet<>();
        try {
            List<String> classNames = IOUtils.readLines(resourceAsStream);
            for (String className : classNames) {
                classes.add((Class<? extends Storable>) Class.forName(className));
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return classes;
    }

    public Collection<ParserInfo> listParsers () {
        return dao.list(PARSER_INFO_NAMESPACE);
    }

    public Collection<ParserInfo> listParsers (List<QueryParam> queryParams) {
        return dao.find(PARSER_INFO_NAMESPACE, queryParams);
    }

    public ParserInfo getParserInfo (Long parserId) {
        ParserInfo parserInfo = new ParserInfo();
        parserInfo.setId(parserId);
        return dao.get(new StorableKey(PARSER_INFO_NAMESPACE, parserInfo.getPrimaryKey()));
    }

    public ParserInfo removeParser (Long parserId) throws IOException {
        ParserInfo parserInfo = new ParserInfo();
        parserInfo.setId(parserId);
        ParserInfo removed = this.dao.remove(new StorableKey(PARSER_INFO_NAMESPACE, parserInfo.getPrimaryKey()));
        if (removed != null) {
            fileStorage.deleteFile(removed.getJarStoragePath());
        }
        return removed;
    }

    public ParserInfo addParser (ParserInfo parserInfo, boolean schemaFromParserJar, InputStream inputStream) throws IOException {
        LOG.debug("schemaFromParser {}", schemaFromParserJar);
        String uploadedPath = fileStorage.uploadFile(inputStream, parserInfo.getJarStoragePath());
        LOG.debug("Jar file uploaded to {}", uploadedPath);
        // force to load parser class so that we don't store ParserInfo when classloader can't load parser class
        Schema schema = loadSchemaFromParserJar(parserInfo.getJarStoragePath(), parserInfo.getClassName());
        // if schema is not set in json, try to load it from the jar just uploaded.
        if (parserInfo.getParserSchema() == null && schemaFromParserJar) {
            parserInfo.setParserSchema(schema);
        }
        if (parserInfo.getId() == null) {
            parserInfo.setId(this.dao.nextId(PARSER_INFO_NAMESPACE));
        }
        if (parserInfo.getTimestamp() == null) {
            parserInfo.setTimestamp(System.currentTimeMillis());
        }
        this.dao.add(parserInfo);
        return parserInfo;
    }

    public InputStream getParserJar (ParserInfo parserInfo) throws IOException {
         return fileStorage.downloadFile(parserInfo.getJarStoragePath());
    }

    public Collection<String> verifyParserUpload (InputStream inputStream) throws IOException {
        final File tmpFile = FileUtil.writeInputStreamToTempFile(inputStream, ".jar");
        return ProxyUtil.canonicalNames(ProxyUtil.loadAllClassesFromJar(tmpFile, Parser.class));
    }

    /**
     * Loads the parser jar and invoke the {@link Parser#schema} method
     * to get the schema.
     */
    private Schema loadSchemaFromParserJar(String jarName, String className) {
        OutputStream os = null;
        InputStream is = null;
        try {
            File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".jar");
            tmpFile.deleteOnExit();
            os = new FileOutputStream(tmpFile);
            is = fileStorage.downloadFile(jarName);
            ByteStreams.copy(is, os);
            Parser parser = parserProxyUtil.loadClassFromJar(tmpFile.getAbsolutePath(), className);
            return parser.schema();
        } catch (Exception ex) {
            LOG.error("Got exception", ex);
            throw new RuntimeException("Cannot load parser class from uploaded Jar: " + className);
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException ex) {
                LOG.error("Got exception", ex);
            }
        }
    }
}
