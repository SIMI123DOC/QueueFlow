
package dao;

import entity.*;
import exception.*;
import java.util.List;

public interface IQueueFlowService {

    void RegisterAgent(Agent agent, List<Integer> skillIds);

    void UpdateAgentAvailability(int agentId, String status)
            throws AgentNotFoundException;

    Request SubmitRequest(int customerId, int requiredSkillId,
                          String priority, String channel);

    Agent RouteRequest(int requestId)
            throws NoAgentsAvailableException;

    void AssignAgent(int requestId, int agentId)
            throws AgentNotFoundException, NoAgentsAvailableException;

    void CompleteRequest(int requestId)
            throws AgentNotFoundException;

    void EscalateRequest(int requestId, String reason);

    void PauseAgent(int agentId, String reason)
            throws AgentNotFoundException;
}
