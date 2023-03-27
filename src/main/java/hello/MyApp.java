package hello;

import com.slack.api.Slack;
import com.slack.api.bolt.App;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class MyApp {
    private static final String ZOHO_OAUTHTOKEN = "1000.043b3a05c6e86a7da5a8a71377334a49.cb0111ad2f78ec0355bf648b38ed81d1";

    public static void main(String[] args) throws Exception {
        // App expects env variables (SLACK_BOT_TOKEN, SLACK_SIGNING_SECRET)
        App app = new App();

        /**
         * Trying to create a custom command (e.g. say hello to channel)
         */
        app.command("/greetings", (req, ctx) -> {
            return ctx.ack(":wave: Hello!");
        });


        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd-uuuu");
        LocalDate localDate = LocalDate.now();
        String date =  dtf.format(localDate);

        //start zoho
        URL url;
        HttpURLConnection request = null;
        String method = "GET";
        String parameters = "";
        String path = "";
        String toInsert = "";
        try {
            /*
             * Set the URL and Parameters to create connection
             * Set Request Method (GET, POST or DELETE)
             */
            if("GET".equals(method))
            {
                parameters = "users_list=765698152&view_type=month&date=" + date + "&bill_status=All&component_type=task";
                path = "/portal/684142557/projects/1401927000000053229/logs/?";
                url = new URL("https://projectsapi.zoho.com/restapi" + path + parameters);
                request = (HttpURLConnection) url.openConnection();
                request.setRequestMethod("GET");
            }

            // add request header
            request.setRequestProperty("Accept", "application/json");
            request.setRequestProperty("Authorization", "Zoho-oauthtoken " + ZOHO_OAUTHTOKEN);
            request.setDoOutput(true);
            request.setDoInput(true);
            request.connect();

            // Get Response
            BufferedReader bf = new BufferedReader(new InputStreamReader(request.getInputStream()));
            String line;
            StringBuffer response = new StringBuffer();
            while((line = bf.readLine())!=null) {
                response.append(line);
                response.append('\r');
            }
            bf.close();

            //Json parser
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(response.toString());

            JSONObject timelogsObject = (JSONObject) jsonObject.get("timelogs");

            String grandTotal = (String) timelogsObject.get("grandtotal");
            String billableHours = (String) timelogsObject.get("billable_hours");
            String nonBillableHours = (String) timelogsObject.get("non_billable_hours");
            String toConcatenate = "Do Viet Anh" + "\n" + "Celkový čas:" + " " + grandTotal +  "\n" + "Billable hours:" + " " + billableHours + "\n" + "Non Billable hours:" + " " + nonBillableHours;

            ByteBuffer buffer = StandardCharsets.UTF_8.encode(toConcatenate);
            toInsert = StandardCharsets.UTF_8.decode(buffer).toString();


            System.out.println(toInsert);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        finally {
            if(request!=null) {
                request.disconnect();
            }
        }

        String finalToInsert = toInsert;
        app.command("/timelog", (req, ctx) -> {
            return ctx.ack(finalToInsert);
        });

        Slack slack = Slack.getInstance();

        String webhookUrl = "https://hooks.slack.com/services/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";
        //String webhookUrl = System.getenv("SLACK_WEBHOOK_URL");
        Payload payload = Payload.builder().text(toInsert).build();

        WebhookResponse response = slack.send(webhookUrl, payload);
        System.out.println(response); // WebhookResponse(code=200, message=OK, body=ok)

        SlackAppServer server = new SlackAppServer(app);
        server.start(); // http://localhost:3000/slack/events
    }
}
