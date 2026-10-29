package com.waykeep.app.io.file.importer;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import com.waykeep.app.data.models.Track;

/**
 * Uses SAX2 to parse XML files.
 * <p>
 * NOTE: SAX2 always closes InputStreams after processing.
 */
public class XMLImporter {

    private static final String TAG = XMLImporter.class.getSimpleName();

    private final TrackParser parser;

    public XMLImporter(TrackParser parser) {
        this.parser = parser;
    }

    @NonNull
    public List<Track.Id> importFile(Context context, Uri uri) throws ImportParserException, ImportAlreadyExistsException, IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            return importFile(inputStream);
        }
    }

    public List<Track.Id> importFile(InputStream inputStream) throws ImportParserException, ImportAlreadyExistsException, IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(true);
            factory.newSAXParser().parse(inputStream, parser.getHandler());
            return parser.getImportTrackIds();
        } catch (SAXException | ParserConfigurationException | ParsingException e) {
            Log.e(TAG, "Unable to import file", e);
            if (!parser.getImportTrackIds().isEmpty()) {
                parser.cleanImport();
            }
            throw new ImportParserException(e);
        } catch (SQLiteConstraintException e) {
            Log.e(TAG, "Unable to import file", e);
            throw new ImportAlreadyExistsException(e);
        }
    }

    interface TrackParser {
        @Deprecated
        DefaultHandler getHandler();

        List<Track.Id> getImportTrackIds();

        void cleanImport();
    }

    /**
     * XXE hardening, best-effort: some platform SAX parsers (seen on HyperOS) do not
     * recognize these features and throw, which must not fail the import — those
     * parsers do not expand external entities in the first place.
     */
    private static void setFeatureIfSupported(SAXParserFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            Log.w(TAG, "SAX parser " + factory.getClass().getName() + " does not support " + name);
        }
    }
}
