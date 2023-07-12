//Run Server:
//mvn org.codehaus.mojo:exec-maven-plugin:exec -Dexec.executable=java -Dexec.args="-cp %classpath io.vertx.core.Launcher run test.project1.Server"
//Stop Server Process:
//netstat -ano | findstr :8080
//taskkill /PID <type PID from last command here> /F
//Send Post Request from CMD:
//curl http://localhost:8080/analyze -d {\"text\":\"word\"}
package test.project1;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;
class CharValueComparator implements Comparator<String> {
    public static int calcuatelettersum(String str) {
    int sum = 0;
    if(str==null){
      return 0;
    }
    for (char strChar : str.toCharArray()) {
      int charValue = (int) strChar;
      if (charValue > 64 && charValue < 91) {
        sum += charValue - 64;
      } else {
        if (charValue > 96 && charValue < 123) {
          sum += charValue - 96;
        }
      }
    }
    return sum;
    }
    // Method
    // To compare two strings
    public int compare(String str1, String str2)
    {
      int charDiff= calcuatelettersum(str1)-calcuatelettersum(str2);
      if(charDiff!=0){
        return charDiff;
      }
      else{
        return str1.compareTo(str2);
      }
    }
}
public class Server extends AbstractVerticle {
  private Router router;
  private HttpServer server;
  private TreeSet<String> requestWordList; //Theorically Possible with Treeset But both soultions are O(N)
  private String returnClosestLexic(String searchWord, TreeSet<String> treeSetWords) {
    if(treeSetWords.size()==0){
      return "";
    }
    if(treeSetWords.contains(searchWord)){
      return searchWord;
    }
    if(searchWord.compareTo(treeSetWords.first())<0){
      return treeSetWords.first();
    }
    if(searchWord.compareTo(treeSetWords.last())>0){
      return treeSetWords.last();
    }
    String lowerString   = treeSetWords.lower(searchWord);
    String higherString  = treeSetWords.higher(searchWord);
    //Bias Towards Lower String
    return searchWord.compareTo(lowerString)<=higherString.compareTo(searchWord)?lowerString:higherString;
  }
  //Function to simplity to search of the most high lexical word
  private static String searchLexicalHighestEqualValue(String startCandiate, TreeSet<String> stringList){
    System.out.println("Starting Searching For Candiates for "+startCandiate);
    String StringCandidate     = startCandiate;
    String StringNext          = stringList.higher(StringCandidate);
    int    startCanidateValue  = CharValueComparator.calcuatelettersum(StringCandidate);
    int    StringNextCharValue = CharValueComparator.calcuatelettersum(StringNext);
    while(StringNext!=null&&StringNextCharValue==startCanidateValue){
      StringCandidate     = StringNext;
      StringNext          = stringList.higher(StringCandidate);
      StringNextCharValue = CharValueComparator.calcuatelettersum(StringNext);
      System.out.println("Checking "+StringCandidate);
    }
    System.out.println("Getting "+StringCandidate);
    return StringCandidate;
  }

  private static String returnClosestValue(String searchWord, TreeSet<String> treeSetWords) {
    if(treeSetWords.size()==0){
      return "";
    }
    if(treeSetWords.size()==1){
      return treeSetWords.first();
    }
    TreeSet<String> treeSetValue=new TreeSet<String>(new CharValueComparator());
    treeSetValue.addAll(treeSetWords);
    if(treeSetValue.contains(searchWord)){
      return searchWord;
    }
    String highestValueString = treeSetValue.last();
    String lowestValueString  = treeSetValue.first();
    int highestCharValue      = CharValueComparator.calcuatelettersum(highestValueString);
    int lowestCharValue       = CharValueComparator.calcuatelettersum(lowestValueString);
    int searchWordValue       = CharValueComparator.calcuatelettersum(searchWord);
    //All words on the list are either low or equal canidates, therefor forcing the lexical highest of the equal, being the last string in the treeset
    if(highestCharValue<=searchWordValue){
      return highestValueString;
    }
    //All words on the list are either high or equal canidates, therefor forcing the lexical highest of the equal, which requires checking all words with the lowest Valuestring for the lexical highest of them
    if(lowestCharValue>=searchWordValue){
      return searchLexicalHighestEqualValue(lowestValueString,treeSetValue);
    }
    String closestStringLow  = treeSetValue.lower(searchWord);
    String closestStringHigh = treeSetValue.higher(searchWord);
    System.out.println("ValueLimit Strings:"+closestStringLow+","+closestStringHigh);
    int charValueClosestLow  = CharValueComparator.calcuatelettersum( closestStringLow);
    int charValueClosestHigh = CharValueComparator.calcuatelettersum( closestStringHigh);
    //If Low are closer, pick the Lexical highest with the equal value to closestStringLow, that being himself (Since otherwise it wouldn't be the closest)
    if(charValueClosestHigh-searchWordValue>searchWordValue-charValueClosestLow){
      System.out.println("Low Chsoen");
      return searchLexicalHighestEqualValue(closestStringLow,treeSetValue);
    }
    else{
      System.out.println("High Chsoen");
      return searchLexicalHighestEqualValue(closestStringHigh,treeSetValue);
    }
  }


  @Override
  public void start(Promise<Void> start) throws Exception {
    File dataFile = new File("wordlst.dat");
    if(!dataFile.exists()|| dataFile.length()==0){
      dataFile.createNewFile();
      requestWordList = new TreeSet<String>();
    }
    else{
      FileInputStream fileInStream   = new FileInputStream("wordlst.dat");
      ObjectInputStream ObjectInStream = new ObjectInputStream(fileInStream);
      requestWordList = (TreeSet<String>)ObjectInStream.readObject();
      ObjectInStream.close();
      fileInStream.close();
    }
    router = Router.router(vertx);
    router.route("/").handler(context -> {
      HttpServerResponse response = context.response();
      response.putHeader("content-type", "text/html")
        .end("<h1>Welcome to my workshop</h1>");
    });
    router.route("/analyze").handler(BodyHandler.create());
    router.post("/analyze").handler(rc -> {
      try {
        rc.response().setChunked(true);
        io.vertx.core.json.JsonObject jsonRequest  = rc.body().asJsonObject();
        io.vertx.core.json.JsonObject jsonResponse = new JsonObject();
        String text = jsonRequest.getString("text");
        if(!(text.matches("[a-zA-Z]+"))){
          System.out.println("Error has occured while processing word");
          rc.response()
          .setStatusCode(422 )
          .end("ERROR:Cannot Process Non-Alphabetic Word Response (Neither Spaces Can Be Processed)");
          return;
        }
        String valueStr,lexicalStr;
        //Naive and synchronous, But it's currently the only reasonable soultion to protect stream's crtical section (Future Variables can't prevent, there's no atomic strings/streams and databases are too heavy for the task)
        synchronized(this){
        valueStr   = returnClosestValue(text, requestWordList);
        lexicalStr = returnClosestLexic(text, requestWordList);
        if (requestWordList.add(text)) {
          System.out.println("Added new word");
        }
        FileOutputStream fileOutStream = new FileOutputStream("wordlst.dat");
        ObjectOutputStream objectOutStream = new ObjectOutputStream(fileOutStream);
        objectOutStream.writeObject(requestWordList);
        objectOutStream.close();
        fileOutStream.close();
        }
        jsonResponse.put("value", valueStr);
        jsonResponse.put("lexical", lexicalStr);
        System.out.println("Word Processed:"+text+",Total words:"+requestWordList.size());
        rc.response()
            .setStatusCode(200)
            .end(jsonResponse.toBuffer());

      } catch (Exception e) {
        System.out.println("Error has occured while processing word");
        rc.response()
          .setStatusCode(500)
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
