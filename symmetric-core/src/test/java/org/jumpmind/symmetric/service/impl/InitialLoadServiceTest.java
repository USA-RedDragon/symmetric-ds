package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InitialLoadServiceTest {
    private InitialLoadService initialLoadService;
    private ISymmetricEngine engine;
    private ISymmetricDialect dialect;
    private IParameterService parameterService;
    private IDatabasePlatform databasePlatform;
    private ISqlTemplate sqlTemplate;
    private ISqlTemplate sqlTemplateDirty;
    private INodeService nodeService;
    private IClusterService clusterService;
    private IStatisticManager statisticManager;
    private IDataService dataService;
    private IConfigurationService configurationService;
    private ITriggerRouterService triggerRouterService;
    private ProcessInfo processInfo;
    private Node identityNode;

    @BeforeEach
    void setup() {
        identityNode = new Node();
        processInfo = new ProcessInfo();
        engine = mock(ISymmetricEngine.class);
        dialect = mock(ISymmetricDialect.class);
        parameterService = mock(IParameterService.class);
        databasePlatform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        nodeService = mock(INodeService.class);
        clusterService = mock(IClusterService.class);
        statisticManager = mock(IStatisticManager.class);
        dataService = mock(IDataService.class);
        configurationService = mock(IConfigurationService.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(dialect.getPlatform()).thenReturn(databasePlatform);
        when(databasePlatform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(statisticManager.newProcessInfo(any())).thenReturn(processInfo);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.isMasterToMaster()).thenReturn(false);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        initialLoadService = new InitialLoadService(engine);
    }

    @Test
    void testGetTriggerRoutersForNodeGroup() {
        String nodeGroupId = "STORES";
        Map<String, List<TriggerRouter>> triggerRoutersByNodeGroup = new HashMap<>();
        TriggerRouter triggerRouter = new TriggerRouter();
        triggerRouter.setRouterId("testRouter");
        triggerRoutersByNodeGroup.put(nodeGroupId, List.of(triggerRouter));
        List<TriggerRouter> actualResult = initialLoadService.getTriggerRoutersForNodeGroup(triggerRoutersByNodeGroup, nodeGroupId);
        assertEquals(1, actualResult.size());
        assertEquals(triggerRouter, actualResult.get(0));
        verify(engine, never()).getTriggerRouterService();
    }

    @Test
    void testGetTriggerRoutersForNodeGroup_withTriggerRouterServiceCall() {
        TriggerRouter triggerRouter = new TriggerRouter();
        triggerRouter.setRouterId("testRouter");
        List<TriggerRouter> triggerRouterForNodeGroup = List.of(triggerRouter);
        String nodeGroupId = "STORES";
        when(parameterService.getNodeGroupId()).thenReturn(nodeGroupId);
        when(triggerRouterService.getAllTriggerRoutersForReloadForCurrentNode(nodeGroupId, nodeGroupId)).thenReturn(triggerRouterForNodeGroup);
        Map<String, List<TriggerRouter>> triggerRoutersByNodeGroup = new HashMap<>();
        triggerRoutersByNodeGroup.put("OTHER", List.of(new TriggerRouter()));
        List<TriggerRouter> actualResult = initialLoadService.getTriggerRoutersForNodeGroup(triggerRoutersByNodeGroup, nodeGroupId);
        assertEquals(1, actualResult.size());
        assertEquals(triggerRouter, actualResult.get(0));
    }

    @Test
    void testGetTriggerRoutersForNodeGroup_withNone() {
        Map<String, List<TriggerRouter>> triggerRoutersByNodeGroup = new HashMap<>();
        triggerRoutersByNodeGroup.put("OTHER", List.of(new TriggerRouter()));
        List<TriggerRouter> actualResult = initialLoadService.getTriggerRoutersForNodeGroup(triggerRoutersByNodeGroup, "STORES");
        when(triggerRouterService.getAllTriggerRoutersForReloadForCurrentNode(any(), any())).thenReturn(Collections.emptyList());
        assertEquals(0, actualResult.size());
    }

    @Test
    void testFindNodesThatAreReadyForInitialLoad() {
        String nodeId = "store123";
        when(nodeService.findIdentityNodeId()).thenReturn(nodeId);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setCreatedAtNodeId(nodeId);
        nodeSecurity.setRegistrationTime(new Date());
        nodeSecurity.setRegistrationEnabled(false);
        nodeSecurity.setInitialLoadEnabled(true);
        when(nodeService.findNodeSecurityWithLoadEnabled()).thenReturn(List.of(nodeSecurity));
        List<NodeSecurity> actualResult = initialLoadService.findNodesThatAreReadyForInitialLoad();
        assertEquals(1, actualResult.size());
        assertEquals(nodeSecurity, actualResult.get(0));
    }

    @Test
    void testFindNodesThatAreReadyForInitialLoad_withNoLoadEnabledNodeSecurity() {
        String nodeId = "store123";
        when(nodeService.findIdentityNodeId()).thenReturn(nodeId);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setCreatedAtNodeId(nodeId);
        nodeSecurity.setRegistrationTime(new Date());
        nodeSecurity.setInitialLoadEnabled(false);
        when(nodeService.findNodeSecurityWithLoadEnabled()).thenReturn(List.of(nodeSecurity));
        List<NodeSecurity> actualResult = initialLoadService.findNodesThatAreReadyForInitialLoad();
        assertEquals(0, actualResult.size());
    }

    @Test
    void testFindNodesThatAreReadyForInitialLoad_withNoRegistrationNodeSecurity() {
        String nodeId = "store123";
        when(nodeService.findIdentityNodeId()).thenReturn(nodeId);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setInitialLoadEnabled(true);
        when(nodeService.findNodeSecurityWithLoadEnabled()).thenReturn(List.of(nodeSecurity));
        List<NodeSecurity> actualResult = initialLoadService.findNodesThatAreReadyForInitialLoad();
        assertEquals(0, actualResult.size());
    }

    @Test
    void testFindNodesThatAreReadyForInitialLoad_withNoLoadEnabledNodeSecurityEmptyList() {
        when(nodeService.findIdentityNodeId()).thenReturn("store123");
        when(nodeService.findNodeSecurityWithLoadEnabled()).thenReturn(Collections.emptyList());
        assertEquals(Collections.emptyList(), initialLoadService.findNodesThatAreReadyForInitialLoad());
    }

    @Test
    void testIsValidLoadTarget() {
        String nodeId = "store123";
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationTime(new Date());
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(nodeSecurity);
        assertTrue(initialLoadService.isValidLoadTarget(nodeId));
    }

    @Test
    void testIsValidLoadTarget_withReverseLoadFirst() {
        String nodeId = "store123";
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationTime(new Date());
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_REVERSE_FIRST)).thenReturn(true);
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(nodeSecurity);
        assertTrue(initialLoadService.isValidLoadTarget(nodeId));
    }

    @Test
    void testIsValidLoadTarget_withReverseLoadQueued() {
        String nodeId = "store123";
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationTime(new Date());
        nodeSecurity.setRevInitialLoadEnabled(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_REVERSE_FIRST)).thenReturn(false);
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(nodeSecurity);
        assertTrue(initialLoadService.isValidLoadTarget(nodeId));
    }

    @Test
    void testIsValidLoadTarget_withReverseLoadFirst_andReverseLoadQueued() {
        String nodeId = "store123";
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationTime(new Date());
        nodeSecurity.setRevInitialLoadEnabled(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_REVERSE_FIRST)).thenReturn(true);
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(nodeSecurity);
        assertFalse(initialLoadService.isValidLoadTarget(nodeId));
    }

    @Test
    void testIsValidLoadTarget_withoutRegistration() {
        String nodeId = "store123";
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(new NodeSecurity());
        assertFalse(initialLoadService.isValidLoadTarget(nodeId));
    }

    @Test
    void testIsValidLoadTarget_withNullNodeSecurity() {
        assertFalse(initialLoadService.isValidLoadTarget("store123"));
    }

    @Test
    void testQueueLoads() {
        when(nodeService.findIdentity()).thenReturn(identityNode);
        when(clusterService.lock(ClusterConstants.INITIAL_LOAD_QUEUE)).thenReturn(true);
        initialLoadService.queueLoads(false);
        verify(engine, times(2)).getClusterService();
        verify(clusterService, times(1)).lock(ClusterConstants.INITIAL_LOAD_QUEUE);
        verify(engine, times(1)).getStatisticManager();
        verify(clusterService, times(1)).unlock(ClusterConstants.INITIAL_LOAD_QUEUE);
        assertEquals(ProcessInfo.ProcessStatus.OK, processInfo.getStatus());
    }

    @Test
    void testQueueLoads_withForce() {
        when(nodeService.findIdentity()).thenReturn(identityNode);
        initialLoadService.queueLoads(true);
        verify(engine, never()).getClusterService(); // never called as it is short-circuited when force == true
        verify(engine, times(1)).getStatisticManager();
        assertEquals(ProcessInfo.ProcessStatus.OK, processInfo.getStatus());
    }

    @Test
    void testQueueLoads_withNullNodeIdentity() {
        when(nodeService.findIdentity()).thenReturn(null);
        initialLoadService.queueLoads(true);
        verify(nodeService, times(1)).findIdentity();
        verify(engine, never()).getClusterService();
        verify(engine, never()).getStatisticManager();
    }

    @Test
    void testQueueLoads_withSyncDisabledNodeIdentity() {
        identityNode.setSyncEnabled(false);
        when(nodeService.findIdentity()).thenReturn(null);
        initialLoadService.queueLoads(true);
        verify(nodeService, times(1)).findIdentity();
        verify(engine, never()).getClusterService();
        verify(engine, never()).getStatisticManager();
    }

    @Test
    void testQueueLoads_withException() {
        when(nodeService.findIdentity()).thenReturn(identityNode);
        when(dataService.getTableReloadRequestToProcess(any())).thenReturn(null); // cause exception
        initialLoadService.queueLoads(true);
        verify(engine, times(1)).getStatisticManager();
        assertEquals(ProcessInfo.ProcessStatus.ERROR, processInfo.getStatus());
    }
}
