package org.mewx.wenku8.global.api;

import org.junit.Test;

class Wenku8ParserTest {
    @Test
    void testParseReviewList() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<page num='12'/>\n" +
                "\n" +
                "<item rid='79800' posttime='20130525171631' replies='1' replytime='20130528184916'>\n" +
                "<user uid='81669'><![CDATA[老衲0轻音]]></user>\n" +
                "<content><![CDATA[前排……]]></content>\n" +
                "</item>\n" +
                "\n" +
                "<item rid='79826' posttime='20130525232002' replies='4' replytime='20130527234259'>\n" +
                "<user uid='34924'><![CDATA[冒险奏鸣]]></user>\n" +
                "<content><![CDATA[有种神曲奏界的既视感]]></content>\n" +
                "</item>\n" +
                "\n" +
                "</metadata>";
        ReviewList reviewList = Wenku8Parser.parseReviewList(xml);

        // TODO: more tests

    }
}
