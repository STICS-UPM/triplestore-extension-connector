package org.eclipse.edc.extension.asset;
import static org.eclipse.edc.spi.result.StoreResult.success;
import static org.eclipse.edc.spi.result.StoreResult.notFound;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.jena.ext.com.google.common.base.Strings;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;

public class SparqlAssetIndexStore extends AbstractSparqlStore<Asset> implements AssetIndex {

    private final static String ASSET_ID_NULL_OR_BLANK = "Asset id must not be null/blank";

    public SparqlAssetIndexStore(ISparqlClient sparqlClient, TypeManager typeManager, String nameGraphIri, String baseNsIri, Monitor monitor) {
        super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, StoreEntity.ASSET);
    }

    @Override
    public StoreResult<Void> create(Asset asset) {
        String id = asset.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(ASSET_ID_NULL_OR_BLANK);

        if (internalAskIfExistsById(id))
            return StoreResult.alreadyExists(AssetIndex.ASSET_EXISTS_TEMPLATE.formatted(id));

        monitor.info("[SparqlAssetIndex] creating Asset: "+asset.toString());
        createUpdate(asset);
        return StoreResult.success();
    }

    @Override
    public StoreResult<Asset> updateAsset(Asset asset) {
        String id = asset.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(ASSET_ID_NULL_OR_BLANK);

        if (!internalAskIfExistsById(id))
            return StoreResult.alreadyExists(AssetIndex.ASSET_NOT_FOUND_TEMPLATE.formatted(id));

        createUpdate(asset);
        return StoreResult.success(asset);
    }

    private void createUpdate(Asset asset) {
        DataAddress dataAddress = asset.getDataAddress();
        String id = asset.getId();
        try {
            internalUpsert(id, asset);
        }catch (Exception e) {
            throw new EdcPersistenceException("Error creating/updating Asset %s".formatted(id), e);
        }
    }



    @Override
    public StoreResult<Asset> deleteById(String assetId) {
        if (Strings.isNullOrEmpty(assetId))
            return StoreResult.generalError(ASSET_ID_NULL_OR_BLANK);

        if (!internalAskIfExistsById(assetId))
            return StoreResult.alreadyExists(AssetIndex.ASSET_NOT_FOUND_TEMPLATE.formatted(assetId));
        Asset existing;
        try {
            existing = internalFindById(assetId, Asset.class);
            if (Objects.isNull(existing))
                return notFound("Asset not found: " + assetId);
            internalDelete(assetId);
        }catch(Exception e){
            throw new EdcPersistenceException("Error deleting Asset %s".formatted(assetId), e);
        }
        return success(existing);
    }

    @Override
    public Stream<Asset> queryAssets(QuerySpec querySpec) {
        return internalFindAll(querySpec, Asset.class);
    }

    @Override
    public Asset findById(String assetId) {
        return internalFindById(assetId, Asset.class);
    }

    @Override
    public DataAddress resolveForAsset(String assetId) {
        Asset asset = internalFindById(assetId, Asset.class);
        return asset.getDataAddress();
    }

    @Override
    public long countAssets(List<Criterion> list) {
        // TODO: Not sure about the possible criterion that could be used
        return 0;
    }




}