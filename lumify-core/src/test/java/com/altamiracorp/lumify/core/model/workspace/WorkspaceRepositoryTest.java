package com.altamiracorp.lumify.core.model.workspace;

import com.altamiracorp.lumify.core.exception.LumifyAccessDeniedException;
import com.altamiracorp.lumify.core.model.ontology.Concept;
import com.altamiracorp.lumify.core.model.ontology.OntologyRepository;
import com.altamiracorp.lumify.core.model.ontology.Relationship;
import com.altamiracorp.lumify.core.model.user.AuthorizationRepository;
import com.altamiracorp.lumify.core.model.user.InMemoryAuthorizationRepository;
import com.altamiracorp.lumify.core.model.user.UserRepository;
import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.securegraph.Vertex;
import com.altamiracorp.securegraph.Visibility;
import com.altamiracorp.securegraph.id.QueueIdGenerator;
import com.altamiracorp.securegraph.inmemory.InMemoryAuthorizations;
import com.altamiracorp.securegraph.inmemory.InMemoryGraph;
import com.altamiracorp.securegraph.inmemory.InMemoryGraphConfiguration;
import com.altamiracorp.securegraph.search.DefaultSearchIndex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;

import static com.altamiracorp.lumify.core.util.CollectionUtil.toList;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WorkspaceRepositoryTest {
    private InMemoryGraph graph;

    @Mock
    private OntologyRepository ontologyRepository;

    @Mock
    private Concept entityConcept;

    @Mock
    private Concept workspaceConcept;

    @Mock
    private Relationship workspaceToEntityRelationship;

    @Mock
    private Relationship workspaceToUserRelationship;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user1;
    private Vertex user1Vertex;

    @Mock
    private User user2;
    private Vertex user2Vertex;

    private QueueIdGenerator idGenerator;

    private WorkspaceRepository workspaceRepository;
    private AuthorizationRepository authorizationRepository;

    @Before
    public void setup() {
        InMemoryGraphConfiguration config = new InMemoryGraphConfiguration(new HashMap());
        idGenerator = new QueueIdGenerator();
        graph = new InMemoryGraph(config, idGenerator, new DefaultSearchIndex(config.getConfig()));
        authorizationRepository = new InMemoryAuthorizationRepository();

        when(ontologyRepository.getConceptByName(eq(OntologyRepository.TYPE_ENTITY))).thenReturn(entityConcept);

        when(ontologyRepository.getOrCreateConcept((Concept) isNull(), eq(WorkspaceRepository.WORKSPACE_CONCEPT_NAME), anyString())).thenReturn(workspaceConcept);
        when(workspaceConcept.getId()).thenReturn(WorkspaceRepository.WORKSPACE_CONCEPT_NAME);

        when(workspaceToEntityRelationship.getId()).thenReturn("workspaceToEntityRelationshipId");
        when(ontologyRepository.getOrCreateRelationshipType(eq(workspaceConcept), eq(entityConcept), eq(WorkspaceRepository.WORKSPACE_TO_ENTITY_RELATIONSHIP_NAME), anyString())).thenReturn(workspaceToEntityRelationship);

        when(workspaceToUserRelationship.getId()).thenReturn("workspaceToUserRelationshipId");
        when(ontologyRepository.getOrCreateRelationshipType(eq(workspaceConcept), eq(entityConcept), eq(WorkspaceRepository.WORKSPACE_TO_USER_RELATIONSHIP_NAME), anyString())).thenReturn(workspaceToUserRelationship);

        workspaceRepository = new WorkspaceRepository(graph, ontologyRepository, userRepository, authorizationRepository);

        String user1Id = "USER_testUser1";
        when(user1.getUserId()).thenReturn(user1Id);
        user1Vertex = graph.addVertex(user1Id, new Visibility(UserRepository.VISIBILITY_STRING), new InMemoryAuthorizations(UserRepository.VISIBILITY_STRING));
        when(userRepository.findById(eq(user1Id))).thenReturn(user1Vertex);

        String user2Id = "USER_testUser2";
        when(user2.getUserId()).thenReturn(user2Id);
        user2Vertex = graph.addVertex(user2Id, new Visibility(UserRepository.VISIBILITY_STRING), new InMemoryAuthorizations(UserRepository.VISIBILITY_STRING));
        when(userRepository.findById(eq(user2Id))).thenReturn(user2Vertex);
    }

    @Test
    public void testAddWorkspace() {
        int startingVertexCount = graph.getAllVertices().size();
        int startingEdgeCount = graph.getAllEdges().size();

        String workspaceId = "testWorkspaceId";
        idGenerator.push(workspaceId);
        idGenerator.push(workspaceId + "_to_" + user1.getUserId());

        Workspace workspace = workspaceRepository.add("workspace1", user1);
        verify(userRepository, times(1)).addAuthorization((Vertex) any(), eq(WorkspaceRepository.WORKSPACE_ID_PREFIX + workspaceId));

        assertEquals(startingVertexCount + 1, graph.getAllVertices().size()); // +1 = the workspace vertex
        assertEquals(startingEdgeCount + 1, graph.getAllEdges().size()); // +1 = the edge between workspace and user1

        assertNull("Should not have access", graph.getVertex(workspace.getId(), new InMemoryAuthorizations()));
        assertNull("Should not have access", graph.getVertex(workspace.getId(), new InMemoryAuthorizations(WorkspaceRepository.VISIBILITY_STRING)));
        InMemoryAuthorizations authorizations = new InMemoryAuthorizations(WorkspaceRepository.VISIBILITY_STRING, workspace.getId());
        assertNotNull("Should have access", graph.getVertex(workspace.getId(), authorizations));

        when(userRepository.getAuthorizations(eq(user1), eq(WorkspaceRepository.VISIBILITY_STRING), eq(workspace.getId()))).thenReturn(authorizations);
        Workspace foundWorkspace = workspaceRepository.findById(workspace.getId(), user1);
        assertEquals(workspace.getId(), foundWorkspace.getId());
    }

    @Test
    public void testFindAllByUser() {
        int startingVertexCount = graph.getAllVertices().size();
        int startingEdgeCount = graph.getAllEdges().size();

        String workspace1Id = "testWorkspace1Id";
        String workspace1Title = "workspace1";
        idGenerator.push(workspace1Id);
        idGenerator.push(workspace1Id + "_to_" + user1.getUserId());
        Workspace workspace1 = workspaceRepository.add(workspace1Title, user1);

        String workspace2Id = "testWorkspace2Id";
        String workspace2Title = "workspace2";
        idGenerator.push(workspace2Id);
        idGenerator.push(workspace2Id + "_to_" + user1.getUserId());
        Workspace workspace2 = workspaceRepository.add(workspace2Title, user1);

        String workspace3Id = "testWorkspace3Id";
        String workspace3Title = "workspace3";
        idGenerator.push(workspace3Id);
        idGenerator.push(workspace3Id + "_to_" + user2.getUserId());
        Workspace workspace3 = workspaceRepository.add(workspace3Title, user2);

        assertEquals(startingVertexCount + 3, graph.getAllVertices().size()); // +3 = the workspace vertices
        assertEquals(startingEdgeCount + 3, graph.getAllEdges().size()); // +3 = the edges between workspaces and users

        InMemoryAuthorizations user1Authorizations = new InMemoryAuthorizations(WorkspaceRepository.VISIBILITY_STRING, workspace1.getId(), workspace2.getId());
        when(userRepository.getAuthorizations(eq(user1), eq(WorkspaceRepository.VISIBILITY_STRING))).thenReturn(user1Authorizations);
        when(userRepository.getAuthorizations(eq(user1), eq(WorkspaceRepository.VISIBILITY_STRING), eq(workspace3.getId()))).thenReturn(user1Authorizations);

        InMemoryAuthorizations user2Authorizations = new InMemoryAuthorizations(WorkspaceRepository.VISIBILITY_STRING, workspace3.getId());
        when(userRepository.getAuthorizations(eq(user2), eq(WorkspaceRepository.VISIBILITY_STRING))).thenReturn(user2Authorizations);
        when(userRepository.getAuthorizations(eq(user2), eq(WorkspaceRepository.VISIBILITY_STRING), eq(workspace3.getId()))).thenReturn(user2Authorizations);

        List<Workspace> user1Workspaces = toList(workspaceRepository.findAll(user1));
        assertEquals(2, user1Workspaces.size());
        boolean foundWorkspace1 = false;
        boolean foundWorkspace2 = false;
        for (Workspace workspace : user1Workspaces) {
            if (workspace.getTitle().equals(workspace1Title)) {
                foundWorkspace1 = true;
            } else if (workspace.getTitle().equals(workspace2Title)) {
                foundWorkspace2 = true;
            } else {
                fail("Invalid workspace " + workspace.getId());
            }
        }
        assertTrue("foundWorkspace1", foundWorkspace1);
        assertTrue("foundWorkspace2", foundWorkspace2);

        List<Workspace> user2Workspaces = toList(workspaceRepository.findAll(user2));
        assertEquals(1, user2Workspaces.size());
        assertEquals(workspace3Title, user2Workspaces.get(0).getTitle());

        try {
            workspaceRepository.updateUserOnWorkspace(user2Workspaces.get(0), user1.getUserId(), WorkspaceAccess.READ, user1);
            fail("user1 should not have access to user2's workspace");
        } catch (LumifyAccessDeniedException ex) {
            assertEquals(user1, ex.getUser());
            assertEquals(user2Workspaces.get(0).getId(), ex.getResourceId());
        }

        idGenerator.push(workspace3Id + "to" + user2.getUserId());
        workspaceRepository.updateUserOnWorkspace(user2Workspaces.get(0), user1.getUserId(), WorkspaceAccess.READ, user2);
        assertEquals(startingVertexCount + 3, graph.getAllVertices().size()); // +3 = the workspace vertices
        assertEquals(startingEdgeCount + 4, graph.getAllEdges().size()); // +4 = the edges between workspaces and users
        List<WorkspaceUser> usersWithAccess = workspaceRepository.findUsersWithAccess(user2Workspaces.get(0), user2);
        boolean foundUser1 = false;
        boolean foundUser2 = false;
        for (WorkspaceUser userWithAccess : usersWithAccess) {
            if (userWithAccess.getUserId().equals(user1.getUserId())) {
                assertEquals(WorkspaceAccess.READ, userWithAccess.getWorkspaceAccess());
                foundUser1 = true;
            } else if (userWithAccess.getUserId().equals(user2.getUserId())) {
                assertEquals(WorkspaceAccess.WRITE, userWithAccess.getWorkspaceAccess());
                foundUser2 = true;
            } else {
                fail("Unexpected user " + userWithAccess.getUserId());
            }
        }
        assertTrue("could not find user1", foundUser1);
        assertTrue("could not find user2", foundUser2);
    }
}
