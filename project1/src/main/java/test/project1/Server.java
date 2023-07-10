//Run Server:
//mvn org.codehaus.mojo:exec-maven-plugin:exec -Dexec.executable=java -Dexec.args="-cp %classpath io.vertx.core.Launcher run test.project1.Server"
//Stop Server Process:
//netstat -ano | findstr :8080
//taskkill /PID <type PID from last command here> /F
//Send Post Request from CMD:
//curl http://localhost:8080/analyze -d {\"text\":\"word\"}
package test.project1;
import java.util.ArrayList;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;

public class Server extends AbstractVerticle {
  private Router router;
  private HttpServer server;
  private ArrayList<String> requestwordList;
  private String returncloestlexic(String str, ArrayList<String> lst) {
    if (lst.isEmpty()) {
      return "";
    }
    if (lst.contains(str)) {
      return str;
    }
    boolean lowflag    = false;
    boolean highflag   = false;
    String  lowstring  = "";
    String  highstring = "";
    for (int i = 0; i < lst.size(); i++) {
      String s = lst.get(i);
      if (s.compareTo(str) < 0) {
        if (!lowflag || lowstring.compareTo(s) < 0) {
          lowstring = s;
          lowflag = true;
        }
      } else {
        if (!highflag || highstring.compareTo(s) > 0) {
          highstring = s;
          highflag = true;
        }
      }
    }
    //Both have to be non negative due to the method the value were assaigned
    int highdiff = highstring.compareTo(str), lowdiff = str.compareTo(lowstring);
    //SOMEHOW we didn't found a word of either type
    if(!highflag&&!lowflag){
      return "";
    }
    //There's A Lowstring AND either there's not a Highstring or it's futher away then the low one (or equal distance since Bias towards low lexic value)
    if(lowflag&&(!highflag||(highflag&&(highdiff >= lowdiff)))){
      return lowstring;
    }
    //Everything else
    return highstring;
  }

  private String returnclosestsum(String str, ArrayList<String> lst) {
    if (lst.isEmpty()) {
      return "";
    }
    int    lowidx    = -1,    highidx = -1;
    String lowstring = "",    highstring = "";
    int    lowvalue  = -1000, highvalue = 1000;
    int    strvalue  = calcuatelettersum(str);
    for (int i = 0; i < lst.size(); i++) {
      String s = lst.get(i);
      int lettersum = calcuatelettersum(s);
      if (lettersum <= strvalue) {
        //Low Words needs to have higher Sum Value to be better candiates
        if (lowidx == -1 || lettersum > lowvalue || (lettersum == lowvalue && s.compareTo(lowstring) > 0)) { 
          lowidx = i;
          lowvalue = lettersum;
          lowstring = s;
        }
      }
      if (lettersum >= strvalue) {
        //High Words needs to have lower Sum Value to be better candiates
        if (highidx == -1 || lettersum < highvalue || (lettersum == highvalue && s.compareTo(highstring) > 0)) { // No
          highidx = i;
          highvalue = lettersum;
          highstring = s;
        }
      }
    }
    if (highidx == -1) {
      return lowidx == -1 ? "" : lowstring;
    } else {
      //Bias Towards The "High" Words, therefore >= and not just >
      return lowidx == -1 || strvalue - lowvalue >= highvalue - strvalue ? highstring : lowstring;
    }
  }

  private int calcuatelettersum(String str) {
    int sum = 0;
    for (char ch : str.toCharArray()) {
      int charvalue = (int) ch;
      if (charvalue > 64 && charvalue < 91) {
        sum += charvalue - 64;
      } else {
        if (charvalue > 96 && charvalue < 123) {
          sum += charvalue - 96;
        }
      }
    }
    return sum;
  }

  @Override
  public void start(Promise<Void> start) throws Exception {
    requestwordList = new ArrayList<String>();
    router          = Router.router(vertx);
    router.route("/").handler(context -> {
      HttpServerResponse response = context.response();
      response.putHeader("content-type", "text/html")
        .end("<h1>Welcome to my workshop</h1>");
    });
    router.route("/analyze").handler(BodyHandler.create());
    router.post("/analyze").handler(rc -> {
      try {
        rc.response().setChunked(true);
        io.vertx.core.json.JsonObject jsonrequest = rc.body().asJsonObject();
        String text = jsonrequest.getString("text");
        io.vertx.core.json.JsonObject jsonobj = new JsonObject();
        jsonobj.put("value", returnclosestsum(text, requestwordList));
        jsonobj.put("lexical", returncloestlexic(text, requestwordList));
        if (!requestwordList.contains(text)) {
          requestwordList.add(text);
        }
        System.out.println("Word");
        rc.response()
            .setStatusCode(200)
            .end(jsonobj.toBuffer());

      } catch (Exception e) {
        rc.response()
            .setStatusCode(500 )
            .end(e.getMessage());
      }
    });
    vertx.createHttpServer().requestHandler(router)
        .listen(config().getInteger("http.port", 8080))
        .onSuccess(server -> {
          this.server = server;
          start.complete();
        })
        .onFailure(start::fail);

  }
}
