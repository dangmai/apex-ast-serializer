package net.dangmai.types;

import cz.habarta.typescript.generator.Extension;
import cz.habarta.typescript.generator.TsProperty;
import cz.habarta.typescript.generator.TsType;
import cz.habarta.typescript.generator.compiler.EnumKind;
import cz.habarta.typescript.generator.compiler.ModelCompiler;
import cz.habarta.typescript.generator.compiler.ModelTransformer;
import cz.habarta.typescript.generator.compiler.Symbol;
import cz.habarta.typescript.generator.emitter.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class modifies how Enum typings are generated to align with how XStream
 * serializes Enums. Basically it wraps Enum types in an interface that looks
 * like `{"$": EnumType, "@class": ClassName}`
 */
public class CustomEnumExtension extends Extension {
    @Override
    public EmitterExtensionFeatures getFeatures() {
        return new EmitterExtensionFeatures();
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return Arrays.asList(
            new TransformerDefinition(
                ModelCompiler.TransformationPhase.BeforeEnums,
                (ModelTransformer) (symbolTable, model) -> transformEnums(model)
            )
        );
    }

    private TsModel transformEnums(TsModel tsModel) {
        final List<TsEnumModel> stringEnums = tsModel.getEnums(EnumKind.StringBased);
        final List<Symbol> enumSymbols = stringEnums
            .stream()
            .map(TsDeclarationModel::getName)
            .collect(Collectors.toList());
        final Map<Symbol, TsEnumModel> enumSymbolTargetMap = stringEnums
            .stream()
            .collect(Collectors.toMap(TsEnumModel::getName, Function.identity()));
        final List<TsBeanModel> beanModels = tsModel.getBeans();
        return tsModel.withBeans(beanModels.stream().map(beanModel -> {
            List<TsPropertyModel> properties = beanModel.getProperties()
                .stream()
                .map(property -> {
                    if (property.tsType instanceof TsType.ReferenceType && enumSymbols.contains(((TsType.ReferenceType) property.tsType).symbol)) {
                        property = property.withTsType(new TsType.ObjectType(
                            new TsProperty("$", property.tsType),
                            new TsProperty(
                                "@class",
                                new TsType.StringLiteralType(
                                    enumSymbolTargetMap.get(((TsType.ReferenceType) property.tsType).symbol).getOrigin().getName()
                                )
                            )
                        ));
                    }
                    return property;
                })
                .collect(Collectors.toList());

            return beanModel.withProperties(properties);
        }).collect(Collectors.toList()));
    }
}
