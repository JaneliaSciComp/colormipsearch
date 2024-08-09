package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.AbstractDao;
import org.janelia.colormipsearch.dao.Dao;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.EntityUtils;
import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.model.BaseEntity;
import org.janelia.colormipsearch.model.annotations.PersistenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract Mongo DAO.
 *
 * @param <T> entity type
 */
public abstract class AbstractMongoDao<T extends BaseEntity> extends AbstractDao<T> implements Dao<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoDao.class);

    protected final MongoCollection<T> mongoCollection;
    protected final IdGenerator idGenerator;

    AbstractMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        mongoCollection = mongoDatabase.getCollection(getEntityCollectionName(), getEntityType());
        this.idGenerator = idGenerator;
    }

    private String getEntityCollectionName() {
        Class<T> entityClass = getEntityType();
        PersistenceInfo persistenceInfo = EntityUtils.getPersistenceInfo(entityClass);
        if (persistenceInfo == null) {
            throw new IllegalArgumentException("Entity class " + entityClass.getName() + " is not annotated with MongoMapping");
        }
        return persistenceInfo.storeName();
    }

    /**
     * This is a placeholder for creating the collection indexes. For now nobody invokes this method.
     */
    abstract protected void createDocumentIndexes();

    @Override
    public T findByEntityId(Number id) {
        return MongoDaoHelper.findById(id, mongoCollection, getEntityType()).block();
    }

    @Override
    public List<T> findByEntityIds(Collection<Number> ids) {
        return MongoDaoHelper.findByIds(ids, mongoCollection, getEntityType()).block();
    }

    @Override
    public PagedResult<T> findAll(Class<T> type, PagedRequest pageRequest) {
        return new PagedResult<>(pageRequest,
                MongoDaoHelper.findAsList(
                        MongoDaoHelper.createFilterByClass(type),
                        MongoDaoHelper.createBsonSortCriteria(pageRequest.getSortCriteria()),
                        true,
                        pageRequest.getOffset(),
                        pageRequest.getPageSize(),
                        mongoCollection,
                        getEntityType()
                ).block()
        );
    }

    @Override
    public long countAll() {
        return MongoDaoHelper.counAll(mongoCollection).block();
    }

    @Override
    public void save(T entity) {
        if (!entity.hasEntityId()) {
            entity.setEntityId(idGenerator.generateId());
            MongoDaoHelper.insertOne(entity, mongoCollection)
                    .map(insertOneResult -> entity)
                    .onErrorMap(t -> {
                        if (t instanceof MongoWriteException) {
                            MongoWriteException mwe = (MongoWriteException) t;
                            if (mwe.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                                throw new IllegalArgumentException(t);
                            }
                        }
                        throw new IllegalStateException(t);
                    })
                    .block()
                    ;
        }
    }

    public void saveAll(List<T> entities) {
        Iterator<Number> idIterator = idGenerator.generateIdList(entities.size()).iterator();
        List<T> toInsert = new ArrayList<>();
        entities.forEach(e -> {
            if (!e.hasEntityId()) {
                e.setEntityId(idIterator.next());
                toInsert.add(e);
            }
        });
        if (!toInsert.isEmpty()) {
            MongoDaoHelper.insertMany(toInsert, mongoCollection)
                    .map(insertResult -> entities)
                    .onErrorMap(t -> {
                        if (t instanceof MongoWriteException) {
                            MongoWriteException mwe = (MongoWriteException) t;
                            if (mwe.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                                throw new IllegalArgumentException(t);
                            }
                        }
                        throw new IllegalStateException(t);
                    })
                    .block()
                    ;
        }
    }

    @Override
    public T update(Number entityId, Map<String, EntityFieldValueHandler<?>> fieldsToUpdate) {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.returnDocument(ReturnDocument.AFTER);
        updateOptions.upsert(false);

        if (fieldsToUpdate.isEmpty()) {
            return findByEntityId(entityId);
        } else {
            return MongoDaoHelper.findOneAndUpdate(
                    getIdMatchFilter(entityId),
                    getUpdates(fieldsToUpdate),
                    updateOptions,
                    mongoCollection
            ).block();
        }
    }

    protected Bson getUpdates(Map<String, EntityFieldValueHandler<?>> fieldsToUpdate) {
        List<Bson> fieldUpdates = fieldsToUpdate.entrySet().stream()
                .map(e -> MongoDaoHelper.getFieldUpdate(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return MongoDaoHelper.combineUpdates(fieldUpdates);
    }

    private Bson getIdMatchFilter(Number id) {
        return Filters.eq("_id", id);
    }

    @Override
    public void delete(T entity) {
        MongoDaoHelper.deleteOne(getIdMatchFilter(entity.getEntityId()), mongoCollection)
                        .block();
    }
}
