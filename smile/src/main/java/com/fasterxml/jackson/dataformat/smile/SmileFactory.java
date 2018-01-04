package com.fasterxml.jackson.dataformat.smile;

import java.io.*;
import java.util.List;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.BinaryNameMatcher;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.sym.FieldNameMatcher;
import com.fasterxml.jackson.core.util.Named;

import com.fasterxml.jackson.dataformat.smile.async.NonBlockingByteArrayParser;

/**
 * Factory used for constructing {@link SmileParser} and {@link SmileGenerator}
 * instances; both of which handle
 * <a href="http://wiki.fasterxml.com/SmileFormat">Smile</a> encoded data.
 *<p>
 * Extends {@link TokenStreamFactory} mostly so that users can actually use it in place
 * of regular non-Smile factory instances.
 *<p>
 * Note on using non-byte-based sources/targets (char based, like
 * {@link java.io.Reader} and {@link java.io.Writer}): these can not be
 * used for Smile-format documents, and thus will either downgrade to
 * textual JSON (when parsing), or throw exception (when trying to create
 * generator).
 * 
 * @author Tatu Saloranta
 */
public class SmileFactory
    extends BinaryTSFactory
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */
    
    /**
     * Name used to identify Smile format.
     * (and returned by {@link #getFormatName()}
     */
    public final static String FORMAT_NAME_SMILE = "Smile";
    
    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_PARSER_FEATURE_FLAGS = SmileParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS = SmileGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Bitmask for {@link SmileParser.Feature}s enabled
     */
    protected final int _formatParserFeatures;

    /**
     * Bitmask for {@link SmileGenerator.Feature}s enabled
     */
    protected final int _formatGeneratorFeatures;

    /*
    /**********************************************************
    /* Symbol table management
    /**********************************************************
     */

    /**
     * Alternative to the basic symbol table, some stream-based
     * parsers use different name canonicalization method.
     */
    protected final transient ByteQuadsCanonicalizer _byteSymbolCanonicalizer = ByteQuadsCanonicalizer.createRoot();

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    /**
     * Default constructor used to create factory instances.
     * Creation of a factory instance is a light-weight operation,
     * but it is still a good idea to reuse limited number of
     * factory instances (and quite often just a single instance):
     * factories are used as context for storing some reused
     * processing objects (such as symbol tables parsers use)
     * and this reuse only works within context of a single
     * factory instance.
     */
    public SmileFactory() {
        _formatParserFeatures = DEFAULT_SMILE_PARSER_FEATURE_FLAGS;
        _formatGeneratorFeatures = DEFAULT_SMILE_GENERATOR_FEATURE_FLAGS;
    }

    public SmileFactory(SmileFactory src)
    {
        super(src);
        _formatParserFeatures = src._formatParserFeatures;
        _formatGeneratorFeatures = src._formatGeneratorFeatures;
    }

    /**
     * Constructors used by {@link SmileFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected SmileFactory(SmileFactoryBuilder b) {
        super(b);
        _formatParserFeatures = b.formatParserFeaturesMask();
        _formatGeneratorFeatures = b.formatGeneratorFeaturesMask();
    }

    @Override
    public SmileFactoryBuilder rebuild() {
        return new SmileFactoryBuilder(this);
    }

    /**
     * Main factory method to use for constructing {@link SmileFactory} instances with
     * different configuration.
     */
    public static SmileFactoryBuilder builder() {
        return new SmileFactoryBuilder();
    }

    @Override
    public SmileFactory copy()
    {
        return new SmileFactory(this);
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     * Also: must be overridden by sub-classes as well.
     */
    protected Object readResolve() {
        return new SmileFactory(this);
    }

    /*
    /**********************************************************
    /* Capability introspection
    /**********************************************************
     */
    
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean canParseAsync() { return true; }
    
    @Override
    public Class<SmileParser.Feature> getFormatReadFeatureType() {
        return SmileParser.Feature.class;
    }

    @Override
    public Class<SmileGenerator.Feature> getFormatWriteFeatureType() {
        return SmileGenerator.Feature.class;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(SmileGenerator.Feature f) {
        return (_formatGeneratorFeatures & f.getMask()) != 0;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(SmileParser.Feature f) {
        return (_formatParserFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************
    /* Format support
    /**********************************************************
     */
    
    @Override
    public String getFormatName() {
        return FORMAT_NAME_SMILE;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false;
    }

    /*
    /**********************************************************
    /* Extended API: async
    /**********************************************************
     */

    @Override
    public NonBlockingByteArrayParser createNonBlockingByteArrayParser(ObjectReadContext readCtxt)
            throws IOException
    {
        ByteQuadsCanonicalizer can = _byteSymbolCanonicalizer.makeChild(_factoryFeatures);
        return new NonBlockingByteArrayParser(readCtxt, _createContext(null, false),
                readCtxt.getParserFeatures(_parserFeatures),
                readCtxt.getFormatReadFeatures(_formatParserFeatures),
                can);
    }

    /*
    /******************************************************
    /* Factory method impls: parsers
    /******************************************************
     */

    /**
     * Overridable factory method that actually instantiates desired parser.
     */
    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in) throws IOException
    {
        return new SmileParserBootstrapper(ioCtxt, in)
            .constructParser(readCtxt, _factoryFeatures,
                    readCtxt.getParserFeatures(_parserFeatures),
                    readCtxt.getFormatReadFeatures(_formatParserFeatures),
                    _byteSymbolCanonicalizer);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len) throws IOException
    {
        return new SmileParserBootstrapper(ioCtxt, data, offset, len)
            .constructParser(readCtxt, _factoryFeatures,
                readCtxt.getParserFeatures(_parserFeatures),
                readCtxt.getFormatReadFeatures(_formatParserFeatures),
                _byteSymbolCanonicalizer);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            DataInput input) throws IOException {
        // 30-Sep-2017, tatu: As of now not supported (but would be possible)
        return _unsupported();
    }

    /*
    /******************************************************
    /* Factory method impls: generators
    /******************************************************
     */

    @Override
    protected JsonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt, OutputStream out) throws IOException
    {
        int smileFeatures = writeCtxt.getFormatWriteFeatures(_formatGeneratorFeatures);
        /* One sanity check: MUST write header if shared string values setting is enabled,
         * or quoting of binary data disabled.
         * But should we force writing, or throw exception, if settings are in conflict?
         * For now, let's error out...
         */
        SmileGenerator gen = new SmileGenerator(writeCtxt, ioCtxt,
                writeCtxt.getGeneratorFeatures(_generatorFeatures),
                smileFeatures,
                out);
        if (SmileGenerator.Feature.WRITE_HEADER.enabledIn(smileFeatures)) {
            gen.writeHeader();
        } else {
            if (SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES.enabledIn(smileFeatures)) {
                throw new JsonGenerationException(
                        "Inconsistent settings: WRITE_HEADER disabled, but CHECK_SHARED_STRING_VALUES enabled; can not construct generator"
                        +" due to possible data loss (either enable WRITE_HEADER, or disable CHECK_SHARED_STRING_VALUES to resolve)",
                        gen);
            }
            if (!SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT.enabledIn(smileFeatures)) {
                throw new JsonGenerationException(
        			"Inconsistent settings: WRITE_HEADER disabled, but ENCODE_BINARY_AS_7BIT disabled; can not construct generator"
        			+" due to possible data loss (either enable WRITE_HEADER, or ENCODE_BINARY_AS_7BIT to resolve)",
        			gen);
            }
        }
        return gen;
    }

    /*
    /******************************************************
    /* Other factory methods
    /******************************************************
     */

    @Override
    public FieldNameMatcher constructFieldNameMatcher(List<Named> matches, boolean alreadyInterned) {
        return BinaryNameMatcher.constructFrom(matches, alreadyInterned);
    }

    @Override
    public FieldNameMatcher constructCIFieldNameMatcher(List<Named> matches, boolean alreadyInterned) {
        return BinaryNameMatcher.constructCaseInsensitive(matches, alreadyInterned);
    }
}
