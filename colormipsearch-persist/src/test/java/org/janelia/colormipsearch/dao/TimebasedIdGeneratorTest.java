package org.janelia.colormipsearch.dao;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TimebasedIdGeneratorTest {

    @Test
    public void generateIdsUsingInParalelUsingALock() {
        IdGenerator idGenerator = new TimebasedIdGenerator(1, "target/id.lock");
        List<Number> ids1 = idGenerator.generateIdList(2* 1024);
        List<Number> ids2 = idGenerator.generateIdList(2 * 1024);
        assertTrue(ids2.get(0).longValue() > ids1.get(ids1.size() - 1).longValue());
        for (int i = 1; i < ids1.size() - 1; i++) {
            assertTrue(ids1.get(i).longValue() > ids1.get(i - 1).longValue());
        }
        for (int i = 1; i < ids2.size() - 1; i++) {
            assertTrue(ids2.get(i).longValue() > ids2.get(i - 1).longValue());
        }
    }
}
