package bio.terra.axonserver.testutils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import bio.terra.common.iam.BearerToken;
// import bio.terra.workspace.model
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@Component
public class MockMvcUtils {
  @Autowired private MockMvc mockMvc;
  public static final BearerToken USER_REQUEST = new BearerToken("FakeBearerToken");
  public static final String AUTH_HEADER = "Authorization";

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, BearerToken userRequest) {
    return request.header(AUTH_HEADER, "Bearer " + userRequest.getToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }

  public String getSerializedResponseForGet(BearerToken userRequest, String formattedPath)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(formattedPath), userRequest))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
