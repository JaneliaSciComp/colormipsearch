package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.AbstractDao;
import org.janelia.colormipsearch.dao.Dao;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.PagedRequest;
import org.janelia.colormipsearch.dao.PagedResult;
import org.janelia.colormipsearch.dao.support.AppendFieldValueHandler;
import org.janelia.colormipsearch.dao.support.EntityUtils;
import org.janelia.colormipsearch.dao.support.IdGenerator;
import org.janelia.colormipsearch.dao.support.IncFieldValueHandler;
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
    private static final String RECORDS_COUNT_FIELD = "recordsCount";

    protected final IdGenerator idGenerator;
    final MongoCollection<T> mongoCollection;

    AbstractMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
        mongoCollection = mongoDatabase.getCollection(getEntityCollectionName(), getEntityType());
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
        return MongoDaoHelper.findById(id, mongoCollection, getEntityType());
    }

    @Override
    public List<T> findByEntityIds(Collection<Number> ids) {
        return MongoDaoHelper.findByIds(ids, mongoCollection, getEntityType());
    }

    @Override
    public PagedResult<T> findAll(PagedRequest pageRequest) {
        return new PagedResult<>(pageRequest,
                MongoDaoHelper.find(
                        null,
                        MongoDaoHelper.createBsonSortCriteria(pageRequest.getSortCriteria()),
                        pageRequest.getOffset(),
                        pageRequest.getPageSize(),
                        mongoCollection,
                        getEntityType()
                )
        );
    }

    @Override
    public long countAll() {
        return mongoCollection.countDocuments();
    }

    <R> Iterable<R> findIterable(Bson queryFilter, Bson sortCriteria, long offset, int length, Class<R> resultType) {
        FindIterable<R> results = mongoCollection.find(resultType);
        if (queryFilter != null) {
            results = results.filter(queryFilter);
        }
        if (offset > 0) {
            results = results.skip((int) offset);
        }
        if (length > 0) {
            results = results.limit(length);
        }
        return results
                .sort(sortCriteria);
    }

    <R> List<R> aggregateAsList(List<Bson> aggregationOperators, Bson sortCriteria, long offset, int length, Class<R> resultType) {
        List<R> results = new ArrayList<>();
        Iterable<R> resultsItr = aggregateIterable(aggregationOperators, sortCriteria, offset, length, resultType);
        resultsItr.forEach(results::add);
        return results;
    }

    <R> Iterable<R> aggregateIterable(List<Bson> aggregationOperators, Bson sortCriteria, long offset, int length, Class<R> resultType) {
        List<Bson> aggregatePipeline = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(aggregationOperators)) {
            aggregatePipeline.addAll(aggregationOperators);
        }
        if (sortCriteria != null) {
            aggregatePipeline.add(Aggregates.sort(sortCriteria));
        }
        if (offset > 0) {
            aggregatePipeline.add(Aggregates.skip((int) offset));
        }
        if (length > 0) {
            aggregatePipeline.add(Aggregates.limit(length));
        }
        return mongoCollection.aggregate(aggregatePipeline, resultType);
    }

    Long countAggregate(List<Bson> aggregationOperators) {
        List<Bson> aggregatePipeline = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(aggregationOperators)) {
            aggregatePipeline.addAll(aggregationOperators);
        }
        aggregatePipeline.add(Aggregates.count(RECORDS_COUNT_FIELD));
        Document recordsCountDoc = mongoCollection.aggregate(aggregatePipeline, Document.class).first();
        if (recordsCountDoc == null) {
            return 0L;
        } else if (recordsCountDoc.get(RECORDS_COUNT_FIELD) instanceof Integer) {
            return recordsCountDoc.getInteger(RECORDS_COUNT_FIELD).longValue();
        } else if (recordsCountDoc.get(RECORDS_COUNT_FIELD) instanceof Long) {
            return recordsCountDoc.getLong(RECORDS_COUNT_FIELD);
        } else {
            LOG.error("Unknown records count field type: {}", recordsCountDoc);
            throw new IllegalStateException("Unknown RECORDS COUNT FIELD TYPE " + recordsCountDoc);
        }
    }

    @Override
    public void save(T entity) {
        if (!entity.hasEntityId()) {
            entity.setEntityId(idGenerator.generateId());
            entity.setCreatedDate(new Date());
            try {
                mongoCollection.insertOne(entity);
            } catch (MongoWriteException e) {
                if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
    }

    public void saveAll(List<T> entities) {
        Iterator<Number> idIterator = idGenerator.generateIdList(entities.size()).iterator();
        List<T> toInsert = new ArrayList<>();
        Date currentDate = new Date();
        entities.forEach(e -> {
            if (!e.hasEntityId()) {
                e.setEntityId(idIterator.next());
                e.setCreatedDate(currentDate);
                toInsert.add(e);
            }
        });
        if (!toInsert.isEmpty()) {
            try {
                mongoCollection.insertMany(toInsert);
            } catch (MongoWriteException e) {
                if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                    throw new IllegalArgumentException(e);
                }
            }
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
            return mongoCollection.findOneAndUpdate(
                    getIdMatchFilter(entityId),
                    getUpdates(fieldsToUpdate),
                    updateOptions
            );
        }
    }

    protected Bson getUpdates(Map<String, EntityFieldValueHandler<?>> fieldsToUpdate) {
        List<Bson> fieldUpdates = fieldsToUpdate.entrySet().stream()
                .map(e -> getFieldUpdate(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return Updates.combine(fieldUpdates);
    }

    private Bson getIdMatchFilter(Number id) {
        return Filters.eq("_id", id);
    }

    @Override
    public void delete(T entity) {
        mongoCollection.deleteOne(getIdMatchFilter(entity.getEntityId()));
    }

    @SuppressWarnings("unchecked")
    private Bson getFieldUpdate(String fieldName, EntityFieldValueHandler<?> valueHandler) {
        if (valueHandler == null || valueHandler.getFieldValue() == null) {
            return Updates.unset(fieldName);
        } else if (valueHandler instanceof AppendFieldValueHandler) {
            Object value = valueHandler.getFieldValue();
            if (value instanceof Iterable) {
                if (Set.class.isAssignableFrom(value.getClass())) {
                    return Updates.addEachToSet(
                            fieldName,
                            StreamSupport.stream(((Iterable<?>) value).spliterator(), false).collect(Collectors.toList())
                    );
                } else {
                    return Updates.pushEach(
                            fieldName,
                            StreamSupport.stream(((Iterable<?>) value).spliterator(), false).collect(Collectors.toList()));
                }
            } else {
                return Updates.push(fieldName, value);
            }
        } else if (valueHandler instanceof IncFieldValueHandler) {
            return Updates.inc(fieldName, (Number) valueHandler.getFieldValue());
        } else {
            return Updates.set(fieldName, valueHandler.getFieldValue());
        }
    }

    Bson createCondExpr(Object cond, Object thenValue, Object elseValue) {
        return new Document("$cond",
                Arrays.asList(
                        cond,
                        thenValue,
                        elseValue
                ));
    }

    Bson createConcatExpr(Object... svalues) {
        return new Document("$concat", Arrays.asList(svalues));
    }

    Bson createEqExpr(Object arg1, Object arg2) {
        return new Document("$eq", Arrays.asList(arg1, arg2));
    }

    Bson createIndexOfExpr(Object expr, Object subExpr) {
        return new Document("$indexOfBytes", Arrays.asList(expr, subExpr));
    }

    Bson createStartsWithExpr(Object expr, Object subExpr) {
        return createEqExpr(createIndexOfExpr(expr, subExpr), 0);
    }

    Bson createSubstrExpr(Object strExpr, Object startIndexExpr, Object countExpr) {
        return new Document("$substrBytes", Arrays.asList(strExpr, startIndexExpr, countExpr));
    }

    Bson createToLowerExpr(Object expr) {
        return new Document("$toLower", expr);
    }

    Bson ifNullExp(Object expr, Object nullDefault) {
        return new Document("$ifNull", Arrays.asList(expr, nullDefault));
    }

    Bson literalExp(Object exp) {
        return new Document("$literal", exp);
    }

}
