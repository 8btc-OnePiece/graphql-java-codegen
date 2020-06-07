package com.kobylynskyi.graphql.codegen;

import com.kobylynskyi.graphql.codegen.mapper.*;
import com.kobylynskyi.graphql.codegen.model.OperationDefinition;
import com.kobylynskyi.graphql.codegen.model.*;
import com.kobylynskyi.graphql.codegen.supplier.MappingConfigSupplier;
import freemarker.template.TemplateException;
import graphql.language.*;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generator of:
 * - Interface for each GraphQL query
 * - Interface for each GraphQL mutation
 * - Interface for each GraphQL subscription
 * - Class for each GraphQL data type
 * - Class for each GraphQL enum type
 * - Class for each GraphQL scalar type
 *
 * @author kobylynskyi
 * @author valinhadev
 */
@Getter
@Setter
public class GraphqlCodegen {

    private List<String> schemas;
    private File outputDir;
    private MappingConfig mappingConfig;
    private MappingConfig result;

    public GraphqlCodegen(List<String> schemas, File outputDir, MappingConfig mappingConfig) {
        this(schemas, outputDir, mappingConfig, null);
    }


    public GraphqlCodegen(List<String> schemas, File outputDir, MappingConfig mappingConfig, MappingConfigSupplier externalMappingConfigSupplier) {
        this.schemas = schemas;
        this.outputDir = outputDir;
        this.mappingConfig = mappingConfig;
        this.mappingConfig.combine(externalMappingConfigSupplier != null ? externalMappingConfigSupplier.get() : null);
        initDefaultValues(mappingConfig);
    }

    public GraphqlCodegen(String[] graphqlSchemaPaths, File outputDir, MappingConfig mappingConfig, MappingConfigSupplier externalMappingConfigSupplier) {
        this.schemas = releaseSchemasInDirPath(graphqlSchemaPaths);
        this.outputDir = outputDir;
        this.mappingConfig = mappingConfig;
        this.mappingConfig.combine(externalMappingConfigSupplier != null ? externalMappingConfigSupplier.get() : null);
        initDefaultValues(mappingConfig);
    }

    private void initDefaultValues(MappingConfig mappingConfig) {
        if (mappingConfig.getModelValidationAnnotation() == null) {
            mappingConfig.setModelValidationAnnotation(DefaultMappingConfigValues.DEFAULT_VALIDATION_ANNOTATION);
        }
        if (mappingConfig.getGenerateEqualsAndHashCode() == null) {
            mappingConfig.setGenerateEqualsAndHashCode(DefaultMappingConfigValues.DEFAULT_EQUALS_AND_HASHCODE);
        }
        if (mappingConfig.getGenerateToString() == null) {
            mappingConfig.setGenerateToString(DefaultMappingConfigValues.DEFAULT_TO_STRING);
        }
        if (mappingConfig.getGenerateApis() == null) {
            mappingConfig.setGenerateApis(DefaultMappingConfigValues.DEFAULT_GENERATE_APIS);
        }
        if (mappingConfig.getGenerateSingleApi() == null) {
            mappingConfig.setGenerateSingleApi(DefaultMappingConfigValues.DEFAULT_GENERATE_SINGLE_QUERY_INTERFACE);
        }
        if (mappingConfig.getNeedDataFetchingEnvironmentParamInSingleApi() == null) {
            mappingConfig.setNeedDataFetchingEnvironmentParamInSingleApi(DefaultMappingConfigValues.DEFAULT_NEED_DATAFETCHINGENVIRONMENT_PARAM_INSINGLEAPI);
        }
    }


    public void generate() throws Exception {
        GraphqlCodegenFileCreator.prepareOutputDir(outputDir);
        for (String schema : schemas) {
            long startTime = System.currentTimeMillis();
            Document document = GraphqlDocumentParser.getDocument(schema);
            addScalarsToCustomMappingConfig(document);
            processDocument(document);
            System.out.println(String.format("Finished processing schema '%s' in %d ms", schema, System.currentTimeMillis() - startTime));
        }
    }

    private List<String> releaseSchemasInDirPath(String[] graphqlSchemaPaths) {
        return Stream.of(graphqlSchemaPaths)
                .flatMap(schemaPath -> {
                    if (Files.isRegularFile(Paths.get(schemaPath))) {
                        return Stream.of(schemaPath);
                    } else {
                        try {
                            return Files.find(Paths.get(schemaPath),
                                    Integer.MAX_VALUE,
                                    (path, basicFileAttributes) -> basicFileAttributes.isRegularFile()
                            ).map(path -> path.toString());
                        } catch (IOException e) {
                            throw new RuntimeException("find graphql file failed");
                        }
                    }
                })
                .filter(path -> path.matches("^.*\\.graphql[s]?$"))
                .collect(Collectors.toList());
    }

    private void processDocument(Document document) throws IOException, TemplateException {
        for (Definition definition : document.getDefinitions()) {
            GraphqlDefinitionType definitionType;
            try {
                definitionType = DefinitionTypeDeterminer.determine(definition);
            } catch (UnsupportedGraphqlDefinitionException ex) {
                continue;
            }
            switch (definitionType) {
                case OPERATION:
                    generateOperation((ObjectTypeDefinition) definition);
                    break;
                case TYPE:
                    generateType((ObjectTypeDefinition) definition, document);
                    break;
                case INTERFACE:
                    generateInterface((InterfaceTypeDefinition) definition);
                    break;
                case ENUM:
                    generateEnum((EnumTypeDefinition) definition);
                    break;
                case INPUT:
                    generateInput((InputObjectTypeDefinition) definition);
                    break;
                case UNION:
                    generateUnion((UnionTypeDefinition) definition);
            }
        }
        System.out.println(String.format("Generated %d definitions in folder '%s'", document.getDefinitions().size(),
                outputDir.getAbsolutePath()));
    }

    private void generateUnion(UnionTypeDefinition definition) throws IOException, TemplateException {
        Map<String, Object> dataModel = UnionDefinitionToDataModelMapper.map(mappingConfig, definition);
        GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.unionTemplate, dataModel, outputDir);
    }

    private void generateInterface(InterfaceTypeDefinition definition) throws IOException, TemplateException {
        Map<String, Object> dataModel = InterfaceDefinitionToDataModelMapper.map(mappingConfig, definition);
        GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.interfaceTemplate, dataModel, outputDir);
    }

    private void generateOperation(ObjectTypeDefinition definition) throws IOException, TemplateException {
        if (mappingConfig.getGenerateApis()) {
            if (mappingConfig.getGenerateSingleApi()) {
                for (FieldDefinition fieldDef : definition.getFieldDefinitions()) {
                    Map<String, Object> dataModel = FieldDefinitionToDataModelMapper.map(mappingConfig, fieldDef, definition.getName());
                    GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.operationsTemplate, dataModel, outputDir);
                }
            }
            // We need to generate a root object to workaround https://github.com/facebook/relay/issues/112
            Map<String, Object> dataModel = ObjectDefinitionToDataModelMapper.map(mappingConfig, definition);
            warpIfNeedDataFetchingEnvioronment(dataModel);
            GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.operationsTemplate, dataModel, outputDir);
        }
    }

    private void warpIfNeedDataFetchingEnvioronment(Map<String, Object> dataModel) {
        if (mappingConfig.getNeedDataFetchingEnvironmentParamInSingleApi()) {
            ((Set<String>) dataModel.get(DataModelFields.IMPORTS)).add("graphql.schema");
            ParameterDefinition parameterDefinition = new ParameterDefinition();
            parameterDefinition.setName("env");
            parameterDefinition.setType("DataFetchingEnvironment");
            for (OperationDefinition operationDefinition : ((List<OperationDefinition>) dataModel.get(DataModelFields.OPERATIONS))) {
                operationDefinition.getParameters().add(parameterDefinition);
            }
        }
    }

    private void generateType(ObjectTypeDefinition definition, Document document) throws IOException, TemplateException {
        Map<String, Object> dataModel = TypeDefinitionToDataModelMapper.map(mappingConfig, definition, document);
        GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.typeTemplate, dataModel, outputDir);
    }

    private void generateInput(InputObjectTypeDefinition definition) throws IOException, TemplateException {
        Map<String, Object> dataModel = InputDefinitionToDataModelMapper.map(mappingConfig, definition);
        GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.typeTemplate, dataModel, outputDir);
    }

    private void generateEnum(EnumTypeDefinition definition) throws IOException, TemplateException {
        Map<String, Object> dataModel = EnumDefinitionToDataModelMapper.map(mappingConfig, definition);
        GraphqlCodegenFileCreator.generateFile(FreeMarkerTemplatesRegistry.enumTemplate, dataModel, outputDir);
    }

    private void addScalarsToCustomMappingConfig(Document document) {
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof ScalarTypeDefinition) {
                String scalarName = ((ScalarTypeDefinition) definition).getName();
                mappingConfig.putCustomTypeMappingIfAbsent(scalarName, "String");
            }
        }
    }

}
