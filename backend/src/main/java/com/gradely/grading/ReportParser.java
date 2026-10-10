package com.gradely.grading;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Report XML is untrusted, even when it comes from an isolated container. */
public final class ReportParser {
    public record TestCase(String name, String status, long durationMs, String message, String stackTrace) {}
    public record Coverage(BigDecimal line, BigDecimal branch) {}
    private ReportParser() {}
    static Document xml(String text) {
        if (text.length()>2_000_000) throw new IllegalArgumentException("Report exceeds limit");
        try {
            var f=DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            var builder=f.newDocumentBuilder();
            builder.setEntityResolver((publicId,systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            var doc=builder.parse(new InputSource(new StringReader(text)));
            if (doc.getDoctype()!=null && doc.getDoctype().getInternalSubset()!=null && !doc.getDoctype().getInternalSubset().isBlank())
                throw new IllegalArgumentException("Internal XML declarations are forbidden");
            return doc;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid grading report",e); }
    }
    public static List<TestCase> junit(Collection<String> reports) {
        var result=new ArrayList<TestCase>(); var names=new HashSet<String>();
        for (String report:reports) {
            var doc=xml(report); String root=doc.getDocumentElement().getTagName();
            if (!Set.of("testsuite","testsuites").contains(root)) throw new IllegalArgumentException("Expected JUnit report");
            var cases=doc.getElementsByTagName("testcase");
            for (int i=0;i<cases.getLength();i++) {
                if (result.size()>=10000) throw new IllegalArgumentException("Too many test cases");
                Element test=(Element)cases.item(i);
                String name=test.getAttribute("classname")+"#"+test.getAttribute("name");
                if (name.length()>512 || test.getAttribute("name").isBlank() || !names.add(name))
                    throw new IllegalArgumentException("Invalid or ambiguous test identity");
                Element failure=child(test,"failure"); if (failure==null) failure=child(test,"error");
                String status=failure!=null ? "FAILED" : child(test,"skipped")!=null ? "SKIPPED" : "PASSED";
                BigDecimal seconds=new BigDecimal(test.getAttribute("time").isBlank() ? "0" : test.getAttribute("time"));
                if (seconds.signum()<0 || seconds.compareTo(BigDecimal.valueOf(86400))>0) throw new IllegalArgumentException("Invalid test duration");
                result.add(new TestCase(name,status,seconds.movePointRight(3).longValue(),failure==null ? null : limit(failure.getAttribute("message"),4000),failure==null ? null : limit(failure.getTextContent(),16000)));
            }
        }
        return List.copyOf(result);
    }
    public static Coverage jacoco(String report) {
        if (report==null) return new Coverage(BigDecimal.ZERO,BigDecimal.ZERO);
        var root=xml(report).getDocumentElement();
        if (!root.getTagName().equals("report")) throw new IllegalArgumentException("Expected JaCoCo report");
        return new Coverage(counter(root,"LINE"),counter(root,"BRANCH"));
    }
    private static BigDecimal counter(Element root,String type) {
        for (Node node=root.getFirstChild();node!=null;node=node.getNextSibling()) {
            if (node instanceof Element e && e.getTagName().equals("counter") && e.getAttribute("type").equals(type)) {
                long missed=Long.parseLong(e.getAttribute("missed")),covered=Long.parseLong(e.getAttribute("covered"));
                if (missed<0 || covered<0 || missed>1_000_000_000 || covered>1_000_000_000) throw new IllegalArgumentException("Invalid coverage count");
                return missed+covered==0 ? BigDecimal.ZERO : BigDecimal.valueOf(covered*100).divide(BigDecimal.valueOf(missed+covered),2,RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }
    public static int violations(String report,String tool) {
        var root=xml(report).getDocumentElement();
        String expected=tool.equals("checkstyle") ? "checkstyle" : "BugCollection";
        if (!root.getTagName().equals(expected)) throw new IllegalArgumentException("Invalid quality report");
        return root.getElementsByTagName(tool.equals("checkstyle") ? "error" : "BugInstance").getLength();
    }
    private static Element child(Element parent,String tag) {
        for (Node node=parent.getFirstChild();node!=null;node=node.getNextSibling()) if (node instanceof Element e && e.getTagName().equals(tag)) return e;
        return null;
    }
    private static String limit(String s,int max) { return s.length()>max ? s.substring(0,max) : s; }
}
