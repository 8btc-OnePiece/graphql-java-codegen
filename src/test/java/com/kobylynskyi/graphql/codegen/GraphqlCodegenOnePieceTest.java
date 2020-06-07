package com.kobylynskyi.graphql.codegen;

import com.kobylynskyi.graphql.codegen.model.MappingConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 扩展时用来测试
 * @author guoyanjiang
 * @date 2020/6/7
 */
public class GraphqlCodegenOnePieceTest {

    @Test
    public void test() {
        MappingConfig mappingConfig = new MappingConfig();
        mappingConfig.setPackageName("ttt.com.onepiece.news.model");
        Map<String, String> customTypesMapping = new HashMap<>();
        customTypesMapping.put("Connection", "graphql.relay.Connection");
        mappingConfig.setCustomTypesMapping(customTypesMapping);
        Map<String, String> customGenericsMapping = new HashMap<>();
        customGenericsMapping.put("connection", "for");
        mappingConfig.setCustomGenericsMapping(customGenericsMapping);
        mappingConfig.setGenerateApis(true);
        mappingConfig.setNeedDataFetchingEnvironmentParamInSingleApi(true);
        mappingConfig.setGenerateEqualsAndHashCode(false);
        mappingConfig.setGenerateToString(false);
        GraphqlCodegen graphqlCodegen = new GraphqlCodegen(new String[]{"/Users/jiangjiangdear/8btc/code/OnePiece/news/news-graph/src/main/resources/graphql"},
                new File("/Users/jiangjiangdear/8btc/code/graphql-java-codegen/src/test/java"), mappingConfig, null);

        try {
            graphqlCodegen.generate();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
