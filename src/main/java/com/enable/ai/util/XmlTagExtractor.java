package com.enable.ai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlTagExtractor {

    /**
     * 从文本中提取指定XML标签的内容
     *
     * @param text    文本内容
     * @param tagName XML标签名称
     * @return 标签内容，如果找不到返回null
     */
    public static String extractXmlTagContent(String text, String tagName) {
        if (text == null || tagName == null || text.isEmpty() || tagName.isEmpty()) {
            return null;
        }

        // 构建正则表达式，匹配标签内容（支持自闭合标签和普通标签）
        String pattern = "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + Pattern.quote(tagName) + ">";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);

        if (m.find()) {
            return m.group(1);
        }

        // 检查自闭合标签
        String selfClosingPattern = "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?/>";
        Pattern selfP = Pattern.compile(selfClosingPattern);
        Matcher selfM = selfP.matcher(text);

        if (selfM.find()) {
            return ""; // 自闭合标签返回空字符串
        }

        return null;
    }

    /**
     * 从文本中提取所有指定XML标签的内容
     *
     * @param text    文本内容
     * @param tagName XML标签名称
     * @return 所有匹配的标签内容列表
     */
    public static List<String> extractAllXmlTagContent(String text, String tagName) {
        List<String> results = new ArrayList<>();

        if (text == null || tagName == null || text.isEmpty() || tagName.isEmpty()) {
            return results;
        }

        // 构建正则表达式
        String pattern = "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + Pattern.quote(tagName) + ">";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);

        while (m.find()) {
            results.add(m.group(1));
        }

        // 检查自闭合标签
        String selfClosingPattern = "<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?/>";
        Pattern selfP = Pattern.compile(selfClosingPattern);
        Matcher selfM = selfP.matcher(text);

        while (selfM.find()) {
            results.add(""); // 自闭合标签添加空字符串
        }

        return results;
    }

    /**
     * 使用简单的字符串操作提取XML标签内容（不使用正则表达式）
     *
     * @param text    文本内容
     * @param tagName XML标签名称
     * @return 标签内容，如果找不到返回null
     */
    public static String extractXmlTagContentSimple(String text, String tagName) {
        if (text == null || tagName == null || text.isEmpty() || tagName.isEmpty()) {
            return null;
        }

        String openTag = "<" + tagName;
        String closeTag = "</" + tagName + ">";

        int startIndex = text.indexOf(openTag);
        if (startIndex == -1) {
            return null;
        }

        // 找到开始标签的结束位置
        int startTagEnd = text.indexOf(">", startIndex);
        if (startTagEnd == -1) {
            return null;
        }

        // 检查是否为自闭合标签
        if (text.charAt(startTagEnd - 1) == '/') {
            return "";
        }

        // 找到结束标签
        int endIndex = text.indexOf(closeTag, startTagEnd);
        if (endIndex == -1) {
            return null;
        }

        return text.substring(startTagEnd + 1, endIndex);
    }

    public static String addXmlTagToUserPrompt(String userPrompt, String promptXmlTag) {
        return promptXmlTag != null ? ("<" + promptXmlTag + ">" + userPrompt + "</" + promptXmlTag + ">") : userPrompt;
    }

    public static boolean containsTag(String content, String tag) {
        return content.contains("<" + tag + ">");
    }

    // 测试方法
    public static void main(String[] args) {
        String xmlText = """
                    <title>这是标题</title>
                    <content>这是内容部分</content>
                    <author name="张三">作者信息</author>
                    <empty/>
                    <nested>
                        <inner>嵌套内容</inner>
                    </nested>
                """;

        // 测试提取单个标签
        System.out.println("内容: " + extractXmlTagContent(xmlText, "content"));
    }
}