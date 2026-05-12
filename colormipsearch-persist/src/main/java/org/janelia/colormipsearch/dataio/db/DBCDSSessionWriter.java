package org.janelia.colormipsearch.dataio.db;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.janelia.colormipsearch.dao.MatchSessionDao;
import org.janelia.colormipsearch.dataio.CDSSessionWriter;
import org.janelia.colormipsearch.dataio.DataSourceParam;
import org.janelia.colormipsearch.model.CDSSessionEntity;

public class DBCDSSessionWriter implements CDSSessionWriter {
    private final MatchSessionDao<CDSSessionEntity> matchSessionDao;

    public DBCDSSessionWriter(MatchSessionDao<CDSSessionEntity> matchSessionDao) {
        this.matchSessionDao = matchSessionDao;
    }

    @Override
    public Number createSession(List<DataSourceParam> masksInputs,
                                List<DataSourceParam> targetsInputs,
                                Map<String, Object> params,
                                Set<String> tags) {
        CDSSessionEntity cdsParametersEntity = new CDSSessionEntity();
        cdsParametersEntity.setUsername(System.getProperty("user.name"));
        cdsParametersEntity.setParams(params);
        cdsParametersEntity.addAllTags(tags);
        masksInputs.stream()
                .map(DataSourceParam::asMap)
                .forEach(cdsParametersEntity::addMask);
        targetsInputs.stream()
                .map(DataSourceParam::asMap)
                .forEach(cdsParametersEntity::addTarget);
        matchSessionDao.save(cdsParametersEntity);
        return cdsParametersEntity.getEntityId();
    }
}
