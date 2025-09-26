package org.janelia.colormipsearch.dao.mongo;

import java.util.List;

import com.mongodb.client.model.Filters;

import org.janelia.colormipsearch.AbstractITest;
import org.janelia.colormipsearch.dao.Dao;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.janelia.colormipsearch.model.BaseEntity;
import org.junit.BeforeClass;

public abstract class AbstractMongoDaoITest extends AbstractITest {
    static DaosProvider daosProvider;

    @BeforeClass
    public static void setUpMongoClient() {
        daosProvider = DaosProvider.getInstance(getITestConfig());
    }

    protected <R extends BaseEntity> void deleteAll(Dao<R> dao, List<R> es) {
        for (R e : es) {
            delete(dao, e);
        }
    }

    protected <R extends BaseEntity> void delete(Dao<R> dao, R e) {
        if (e.hasEntityId()) {
            dao.delete(e);
        }
    }

    protected <R extends BaseEntity> R persistEntity(Dao<R> dao, R e) {
        dao.save(e);
        return e;
    }

    protected <R extends BaseEntity> void deleteArchivedEntries(AbstractMongoDao<R> dao) {
        if (dao.mongoArchiveCollection != null) {
            dao.mongoArchiveCollection.deleteMany(Filters.empty());
        }
    }
}
