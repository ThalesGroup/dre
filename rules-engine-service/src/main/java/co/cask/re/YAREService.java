package co.cask.re;

import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceContext;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.jexl3.JexlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import static co.cask.re.ServiceUtils.sendJson;

/**
 * Class description here.
 */
public class YAREService extends AbstractHttpServiceHandler {
  private static final Logger LOG = LoggerFactory.getLogger(YAREService.class);
  private static final Gson gson = new Gson();

  @UseDataSet("rules")
  private Table rules;

  @UseDataSet("rulebook")
  private Table rulebook;

  private RulesDB rulesDB;

  @Override
  public void initialize(HttpServiceContext context) throws Exception {
    super.initialize(context);
    rulesDB = new RulesDB(rulebook, rules);
  }

  /**
   * This API request is for validating the 'when' clause specified in the expression.
   *
   * @param request to gather information of the request.
   * @param responder to respond to the service request.
   */
  @POST
  @Path("validate-when")
  public void validateWhen(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      ServiceUtils.success(responder, "Valid when clause");
    } catch (JexlException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR, e.getMessage());
    }
  }

  @POST
  @Path("rules")
  public void create(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      RequestExtractor handler = new RequestExtractor(request);
      String content = handler.getContent(StandardCharsets.UTF_8);
      RuleRequest rule = gson.fromJson(content, RuleRequest.class);
      rulesDB.createRule(rule);

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully created rule '%s'.", rule.getId()));
      response.addProperty("count", 1);
      JsonArray values = new JsonArray();
      values.add(new JsonPrimitive(rule.getId()));
      response.add("values", values);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleAlreadyExistsException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
        String.format("Unexpected error while creating rule. Please check your request. %s", e.getMessage())
      );
    }
  }

  @GET
  @Path("rules")
  public void rules(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      List<Map<String, Object>> rules = rulesDB.rules();
      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully listed rules."));
      response.addProperty("count", rules.size());
      response.add("values", gson.toJsonTree(rules));
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while listing rules. Please check your request. %s", e.getMessage())
      );
    }
  }

  @PUT
  @Path("rules/{rule-id}")
  public void update(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("rule-id") String id) {
    try {
      RequestExtractor handler = new RequestExtractor(request);
      String content = handler.getContent(StandardCharsets.UTF_8);
      RuleRequest rule = gson.fromJson(content, RuleRequest.class);
      rulesDB.updateRule(id, rule);

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully updated rule '%s'.", id));
      response.addProperty("count", 1);
      JsonArray values = new JsonArray();
      values.add(new JsonPrimitive(id));
      response.add("values", values);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
        String.format("Unexpected error while updating rule. Please check your request. %s", e.getMessage())
      );
    }
  }

  @GET
  @Path("rules/{rule-id}")
  public void retrieve(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rule-id") String id, @QueryParam("format") String format) {
    try {
      Map<String, Object> result = rulesDB.retrieveRule(id);

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully retrieved rule '%s'.", id));
      response.addProperty("count", 1);

      if (format == null || format.equalsIgnoreCase("json")) {
        response.add("values", gson.toJsonTree(result));
      } else {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(rulesDB.retrieveUsingRuleTemplate(id)));
        response.add("values", array);
      }
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
        String.format("Unexpected error while retrieving rule. Please check your request. %s", e.getMessage())
      );
    }
  }

  @DELETE
  @Path("rules/{rule-id}")
  public void delete(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rule-id") String id) {
    try {
      rulesDB.deleteRule(id);
      ServiceUtils.success(responder, String.format("Successfully deleted rule '%s'", id));
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while deleting the rule. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @POST
  @Path("rulebooks")
  public void createRb(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      RequestExtractor handler = new RequestExtractor(request);
      String content = handler.getContent(StandardCharsets.UTF_8);
      String id;
      if(handler.isContentType("application/json")) {
        RulebookRequest rb = gson.fromJson(content, RulebookRequest.class);
        rulesDB.createRulebook(rb);
        id = rb.getId();
      } else if (handler.isContentType("application/rules-engine")) {
        Reader reader = new StringReader(content);
        Compiler compiler = new RulebookCompiler();
        Rulebook rulebook = compiler.compile(reader);
        rulesDB.createRulebook(rulebook);
        id = rulebook.getName();
      } else {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_BAD_REQUEST, "Unsupported content type.");
        return;
      }

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully created rulebook '%s'.", id));
      response.addProperty("count", 1);
      JsonArray values = new JsonArray();
      values.add(new JsonPrimitive(id));
      response.add("values", values);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RulebookAlreadyExistsException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder,HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while creating rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @GET
  @Path("rulebooks")
  public void rulebooks(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      List<Map<String, Object>> rulebooks = rulesDB.rulebooks();

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully listed rulebooks."));
      response.addProperty("count", rulebooks.size());
      response.add("values", gson.toJsonTree(rulebooks));
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while listing rulebooks. Please check your request. %s", e.getMessage())
      );
    }
  }

  @PUT
  @Path("rulebooks/{rulebook-id}")
  public void updateRb(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("rulebook-id") String id) {
    try {
      RequestExtractor handler = new RequestExtractor(request);
      String content = handler.getContent(StandardCharsets.UTF_8);
      RulebookRequest rulebook = gson.fromJson(content, RulebookRequest.class);
      rulesDB.updateRulebook(id, rulebook);

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully updated rule '%s'.", id));
      response.addProperty("count", 1);
      JsonArray values = new JsonArray();
      values.add(new JsonPrimitive(id));
      response.add("values", values);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while updating rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @GET
  @Path("rulebooks/{rulebook-id}")
  public void retrieveRb(HttpServiceRequest request, HttpServiceResponder responder,
                         @PathParam("rulebook-id") String id) {
    try {
      String rulebookString = rulesDB.generateRulebook(id);

      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully generated rulebook '%s'.", id));
      response.addProperty("count", 1);
      JsonArray values = new JsonArray();
      values.add(new JsonPrimitive(rulebookString));
      response.add("values", values);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while generating rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @GET
  @Path("rulebooks/{rulebook-id}/rules")
  public void retrieveRbRules(HttpServiceRequest request, HttpServiceResponder responder,
                              @PathParam("rulebook-id") String id) {
    try {
      JsonArray rules = rulesDB.getRulebookRules(id);
      JsonObject response = new JsonObject();
      response.addProperty("status", HttpURLConnection.HTTP_OK);
      response.addProperty("message", String.format("Successfully listed rules for the rulebook '%s'.", id));
      response.addProperty("count", rules.size());
      response.add("values", rules);
      sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
    } catch (RuleNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while generating rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @DELETE
  @Path("rulebooks/{rulebook-id}")
  public void deleteRb(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("rulebook-id") String id) {
    try {
      rulesDB.deleteRulebook(id);
      ServiceUtils.success(responder, String.format("Successfully deleted rulebook '%s'", id));
    } catch (RulebookNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while deleting the rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @PUT
  @Path("rulebooks/{rulebook-id}/rules/{rule-id}")
  public void addRuleToRb(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rulebook-id") String rbId, @PathParam("rule-id") String id) {
    try {
      rulesDB.addRuleToRulebook(rbId, id);
      ServiceUtils.success(responder, String.format("Successfully added rule '%s' to rulebook '%s'", id, rbId));
    } catch (RulebookNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while adding rule to rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }

  @DELETE
  @Path("rulebooks/{rulebook-id}/rules/{rule-id}")
  public void deleteRuleFromRb(HttpServiceRequest request, HttpServiceResponder responder,
                          @PathParam("rulebook-id") String rbId, @PathParam("rule-id") String id) {
    try {
      rulesDB.removeRuleFromRulebook(rbId, id);
      ServiceUtils.success(responder, String.format("Successfully removed rule '%s' to rulebook '%s'", id, rbId));
    } catch (RulebookNotFoundException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                         String.format("Unexpected error while removing rule from rulebook. " +
                                         "Please check your request. %s", e.getMessage())
      );
    }
  }
}
