package com.composum.sling.dashboard.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.query.Query;

import static com.composum.sling.dashboard.util.JcrQuery.FULL_TEXT;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class JcrQueryTest {

    @SuppressWarnings("deprecation")
    @Test
    public void queryTypeTest() {
        JcrQuery query;
        query = new JcrQuery("/content/path -ignore \"a text\"");
        assertEquals(FULL_TEXT, query.getQueryType());
        query = new JcrQuery("\"a text\"");
        assertEquals(FULL_TEXT, query.getQueryType());
        query = new JcrQuery("text");
        assertEquals(FULL_TEXT, query.getQueryType());
        query = new JcrQuery("/a/xpath/query");
        assertEquals(Query.XPATH, query.getQueryType());
        query = new JcrQuery("/a/xpath/query//*");
        assertEquals(Query.XPATH, query.getQueryType());
        query = new JcrQuery("/a/xpath/query//element(*,nt:resource)");
        assertEquals(Query.XPATH, query.getQueryType());
        query = new JcrQuery("/jcr:root/a/xpath/query");
        assertEquals(Query.XPATH, query.getQueryType());
        query = new JcrQuery("UNION SELECT something");
        assertEquals(Query.JCR_SQL2, query.getQueryType());
        query = new JcrQuery("select something");
        assertEquals(Query.JCR_SQL2, query.getQueryType());
    }

    @Test
    public void buildTextDebug() {
        JcrQuery query;
        query = new JcrQuery("-\"a text\"");
        assertEquals("select * from [nt:base] as node where isdescendantnode(node, '/content') and (not [*] like '%a text%')",
                query.getTextQuery());
    }

    @Test
    public void buildTextQueryTest() {
        JcrQuery query;
        query = new JcrQuery("/content/path -><ignore -[text]\"a text\" +word and \"a text sentence\" +more alternatives");
        assertEquals("select * from [nt:base] as node where isdescendantnode(node, '/content/path')" +
                        " and (not [*]<>'ignore' and not [text] like '%a text%'" +
                        " or contains([*],'word') and contains([*],'and') and [*] like '%a text sentence%'" +
                        " or contains([*],'more') and contains([*],'alternatives'))",
                query.getQuery());
        query = new JcrQuery("[cq:Page] -ignore \"a text\" +word and [jcr:title]=\"a text sentence\" + =>more [text]alternatives");
        assertEquals("select * from [cq:Page] as node where isdescendantnode(node, '/content')" +
                        " and (not contains([*],'ignore') and [*] like '%a text%'" +
                        " or contains([*],'word') and contains([*],'and') and [jcr:title]='a text sentence'" +
                        " or [*]>='more' and contains([text],'alternatives'))",
                query.getQuery());
        query = new JcrQuery("- [text]\"a text\"");
        assertEquals("select * from [nt:base] as node where isdescendantnode(node, '/content')" +
                        " and (not [text] like '%a text%')",
                query.getQuery());
        query = new JcrQuery("*text*");
        assertEquals("select * from [nt:base] as node where isdescendantnode(node, '/content')" +
                        " and ([*] like '%text%')",
                query.getQuery());
        query = new JcrQuery("[nt:resource] \"text\"");
        assertEquals("select * from [nt:resource] as node where isdescendantnode(node, '/content')" +
                        " and ([*] like '%text%')",
                query.getQuery());
        query = new JcrQuery("[jcr:title]text*");
        assertEquals("select * from [nt:base] as node where isdescendantnode(node, '/content')" +
                        " and ([jcr:title] like 'text%')",
                query.getQuery());
    }
}
