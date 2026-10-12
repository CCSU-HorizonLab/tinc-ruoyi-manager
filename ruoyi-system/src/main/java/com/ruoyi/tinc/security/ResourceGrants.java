package com.ruoyi.tinc.security;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Complete replacement of one user's grants; null is not an implicit revoke-all. */
public class ResourceGrants {
    @NotNull @Size(max = 500)
    private List<Long> serverIds;
    @NotNull @Size(max = 500)
    private List<Long> networkIds;
    public List<Long> getServerIds() { return serverIds; }
    public void setServerIds(List<Long> ids) { serverIds = ids; }
    public List<Long> getNetworkIds() { return networkIds; }
    public void setNetworkIds(List<Long> ids) { networkIds = ids; }
}
