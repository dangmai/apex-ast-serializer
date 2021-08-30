package net.dangmai.types;

import cz.habarta.typescript.generator.Extension;
import cz.habarta.typescript.generator.TsType;
import cz.habarta.typescript.generator.compiler.ModelCompiler;
import cz.habarta.typescript.generator.compiler.ModelTransformer;
import cz.habarta.typescript.generator.emitter.EmitterExtensionFeatures;
import cz.habarta.typescript.generator.emitter.TsBeanModel;
import cz.habarta.typescript.generator.emitter.TsModifierFlags;
import cz.habarta.typescript.generator.emitter.TsPropertyModel;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to customize the Typescript type generations from jorje
 * Java classes.
 */
public class CustomTypeGeneratorExtension extends Extension {
    @Override
    public EmitterExtensionFeatures getFeatures() {
        return new EmitterExtensionFeatures();
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return Arrays.asList(
            new TransformerDefinition(
                ModelCompiler.TransformationPhase.BeforeSymbolResolution,
                (ModelTransformer) (symbolTable, model) -> model.withBeans(
                    model.getBeans().stream()
                        .map(CustomTypeGeneratorExtension.this::addCustomProperties)
                        .collect(Collectors.toList())
            ))
        );
    }

    private TsBeanModel addCustomProperties(TsBeanModel bean) {
        Class<?> originClass = bean.getOrigin();
        List<TsPropertyModel> allProperties = bean.getProperties();
        allProperties.add(new TsPropertyModel("@id", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        allProperties.add(new TsPropertyModel("@reference", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        if (!originClass.getName().startsWith("apex.jorje")) {
            return bean;
        }
        Boolean isInterface = bean.getOrigin().isInterface();
        Boolean isAbstract = Modifier.isAbstract(originClass.getModifiers());

        if (isInterface || isAbstract) {
            allProperties.add(new TsPropertyModel("@class", new TsType.OptionalType(TsType.String), TsModifierFlags.None, true, null));
        } else {
            allProperties.add(new TsPropertyModel("@class", new TsType.StringLiteralType(bean.getOrigin().getName()), TsModifierFlags.None, true, null));
        }
        return bean
            .withProperties(allProperties);
    }
}
