package net.dangmai.serializer;

import apex.jorje.semantic.ast.compilation.*;
import apex.jorje.semantic.ast.visitor.AdditionalPassScope;
import apex.jorje.semantic.ast.visitor.AstVisitor;
import apex.jorje.semantic.common.EmptySymbolProvider;
import apex.jorje.semantic.common.TestAccessEvaluator;
import apex.jorje.semantic.common.TestQueryValidators;
import apex.jorje.semantic.compiler.ApexCompiler;
import apex.jorje.semantic.compiler.CodeUnit;
import apex.jorje.semantic.compiler.CompilationInput;
import apex.jorje.semantic.compiler.SourceFile;
import apex.jorje.semantic.compiler.parser.ParserEngine;
import apex.jorje.semantic.compiler.parser.ParserOutput;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.json.JsonWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;
import java.util.logging.LogManager;

public class Apex {
    private static void setUpXStream(XStream xstream, int mode) {
        xstream.setMode(mode);
        xstream.registerConverter(new CustomCollectionConverter(xstream.getMapper()));
    }

    public static void main(String[] args) throws ParseException, IOException {
        // Disable logging, otherwise jorje puts logs onto stderr,
        // which makes the calling code thinks that something is wrong
        LogManager.getLogManager().reset();

        Options cliOptions = new Options();
        cliOptions.addOption("a", "anonymous", false, "Parse Anonymous Apex code. If not specify, it will be parsed in Named mode.");
        cliOptions.addOption("f", "format", true, "Format of the output. Possible options: json, xml.");
        cliOptions.addOption("l", "location", true, "Location of Apex class file. If not specified, the Apex content will be read from stdin.");
        cliOptions.addOption("p", "pretty", false, "Pretty print output.");
        cliOptions.addOption("i", "id-ref", false, "Use ID reference rather than XPath.");
        cliOptions.addOption("h", "help", false, "Print help information.");

        CommandLineParser cliParser = new DefaultParser();
        CommandLine cmd = cliParser.parse(cliOptions, args);
        Reader apexReader;

        Set<String> acceptedFormats = new HashSet<>(Arrays.asList("xml", "json"));
        String chosenFormat = cmd.getOptionValue("f");

        if (cmd.hasOption("h")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("apex-ast-serializer", cliOptions);
        } else if (!cmd.hasOption("f") || !acceptedFormats.contains(chosenFormat)) {
            System.out.println("Format not specified or not supported.");
        } else {
            if (cmd.hasOption("l")) {
                apexReader = new FileReader(cmd.getOptionValue("l"));
            } else {
                apexReader = new BufferedReader(new InputStreamReader(System.in));
            }

            String sourceCode = IOUtils.toString(apexReader);
            apexReader.close();
            SourceFile sourceFile = SourceFile.builder().setBody(sourceCode).build();
            ParserEngine engine;
            if (cmd.hasOption("a")) {
                engine = ParserEngine.get(ParserEngine.Type.ANONYMOUS);
            } else {
                engine = ParserEngine.get(ParserEngine.Type.NAMED);
            }
            ParserOutput output = engine.parse(
                    sourceFile,
                    ParserEngine.HiddenTokenBehavior.COLLECT_COMMENTS
            );
            CompilationInput compilationInput = new CompilationInput(
                    Arrays.asList(sourceFile),
                    EmptySymbolProvider.get(),
                    new TestAccessEvaluator(),
                    new TestQueryValidators.Noop(),
                    new TopLevelVisitor()
            );
            ApexCompiler compiler = ApexCompiler.builder()
                    .setInput(compilationInput)
                    .setHiddenTokenBehavior(ParserEngine.HiddenTokenBehavior.COLLECT_COMMENTS)
                    .build();
            List<CodeUnit> codeUnits = compiler.compile();

            // Serializing the output
            int mode;
            if (cmd.hasOption("i")) {
                mode = XStream.ID_REFERENCES;
            } else {
                mode = XStream.XPATH_ABSOLUTE_REFERENCES;
            }
            Mapper defaultMapper = (new XStream()).getMapper();
            XStream xstream;

            if (chosenFormat.equals("json")) {
                xstream = new XStream(
                        null,
                        new JsonHierarchicalStreamDriver() {
                            @Override
                            public HierarchicalStreamWriter createWriter(Writer writer) {
                                if (cmd.hasOption("p")) {
                                    // By default JSON is pretty printed
                                    return super.createWriter(writer);
                                }
                                JsonWriter.Format format = new JsonWriter.Format(
                                        new char[0],
                                        "".toCharArray(),
                                        JsonWriter.Format.SPACE_AFTER_LABEL | JsonWriter.Format.COMPACT_EMPTY_ELEMENT
                                );
                                return new JsonWriter(writer, format);
                            }
                        },
                        new ClassLoaderReference(new CompositeClassLoader()),
                        new WithClassMapper(defaultMapper));
                setUpXStream(xstream, mode);

                System.out.println(xstream.toXML(output));
            } else {
                xstream = new XStream();
                setUpXStream(xstream, mode);

                if (cmd.hasOption("p")) {
                    System.out.println(xstream.toXML(output));
                } else {
                    StringWriter writer = new StringWriter();
                    xstream.marshal(output, new CompactWriter(writer));
                    System.out.print(writer.toString());
                }
            }
        }
    }

    // We use this mapper to make sure all classes in the jorje package gets printed out as class attribute
    static private class WithClassMapper extends MapperWrapper {
        WithClassMapper(Mapper wrapped) {
            super(wrapped);
        }

        @Override
        public Class defaultImplementationOf(Class type) {
            if (type.getPackage() != null && type.getPackage().getName().startsWith("apex.jorje")) {
                return FakeDefaultImplementation.class;
            }
            return super.defaultImplementationOf(type);
        }

        @Override
        public boolean shouldSerializeMember(Class definedIn, String fieldName) {
            return true;
        }
    }

    private class FakeDefaultImplementation {
    }

    // Custom Collection Converter to inject the class attribute on the collection elements
    static class CustomCollectionConverter extends CollectionConverter {
        private static final Class SINGLETON_LIST = Collections.singletonList(Boolean.TRUE).getClass();
        private static final Class SINGLETON_SET = Collections.singleton(Boolean.TRUE).getClass();

        public CustomCollectionConverter(Mapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canConvert(Class type) {
            return Collection.class.isAssignableFrom(type) || super.canConvert(type);
        }

        protected void writeItem(Object item, MarshallingContext context, HierarchicalStreamWriter writer) {
            // Copied from AbstractCollectionConverter
            if (item == null) {
                String name = mapper().serializedClass(null);
                ExtendedHierarchicalStreamWriterHelper.startNode(writer, name, Mapper.Null.class);
                writer.endNode();
            } else {
                String name = mapper().serializedClass(item.getClass());
                ExtendedHierarchicalStreamWriterHelper.startNode(writer, name, item.getClass());
                writer.addAttribute("class", name);
                context.convertAnother(item);
                writer.endNode();
            }
        }
    }
    private static class TopLevelVisitor extends AstVisitor<AdditionalPassScope> {
        Compilation topLevel;

        public Compilation getTopLevel() {
            return topLevel;
        }

        @Override
        public void visitEnd(UserClass node, AdditionalPassScope scope) {
            topLevel = node;
        }

        @Override
        public void visitEnd(UserEnum node, AdditionalPassScope scope) {
            topLevel = node;
        }

        @Override
        public void visitEnd(UserInterface node, AdditionalPassScope scope) {
            topLevel = node;
        }

        @Override
        public void visitEnd(UserTrigger node, AdditionalPassScope scope) {
            topLevel = node;
        }
    }
}
