package bio.terra.axonserver.testutils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.common.iam.BearerToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

  public String getSerializedResponseForPost(
      BearerToken userRequest, String formattedPath, String request) throws Exception {
    return mockMvc
        .perform(
            addAuth(
                post(formattedPath)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                userRequest))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForGetExpect(
      BearerToken userRequest, String formattedPath, int code) throws Exception {
    return mockMvc
        .perform(addAuth(get(formattedPath), userRequest))
        .andExpect(MockMvcResultMatchers.status().is(code))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
