package net.dangmai.serializer;

import cz.habarta.typescript.generator.Extension;
import cz.habarta.typescript.generator.TsType;
import cz.habarta.typescript.generator.compiler.ModelCompiler;
import cz.habarta.typescript.generator.compiler.ModelTransformer;
import cz.habarta.typescript.generator.compiler.SymbolTable;
import cz.habarta.typescript.generator.emitter.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to customize the Typescript type generations from jorje
 * Java classes.
 */
public class CustomTypingGenerator extends Extension {
    @Override
    public EmitterExtensionFeatures getFeatures() {
        final EmitterExtensionFeatures features = new EmitterExtensionFeatures();
        return features;
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return Arrays.asList(
            new TransformerDefinition(ModelCompiler.TransformationPhase.BeforeSymbolResolution, new ModelTransformer() {
                @Override
                public TsModel transformModel(SymbolTable symbolTable, TsModel model) {
                    return model.withBeans(model.getBeans().stream()
                        .map(CustomTypingGenerator.this::addCustomProperties)
                        .collect(Collectors.toList())
                    );
                }
            })
        );
    }

    private TsBeanModel addCustomProperties(TsBeanModel bean) {
        Class originClass = bean.getOrigin();
        if (!originClass.getName().startsWith("apex")) {
            return bean;
        }
        List<TsPropertyModel> allProperties = bean.getProperties();
        Boolean isInterface = bean.getOrigin().isInterface();
        Boolean isAbstract = Modifier.isAbstract(originClass.getModifiers());

        if (isInterface || isAbstract) {
            allProperties.add(new TsPropertyModel("@class", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        } else {
            allProperties.add(new TsPropertyModel("@class", new TsType.StringLiteralType(bean.getOrigin().getName()), TsModifierFlags.None, true, null));
        }
        allProperties.add(new TsPropertyModel("@id", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        allProperties.add(new TsPropertyModel("@reference", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        return bean
            .withProperties(allProperties);
    }

}
