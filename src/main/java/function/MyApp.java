package function;

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
    /**
     * Having some credentials here to experiment with authorization.
     * Of course credentials should not be "hard-coded", but at least should be used
     * in the code as environment variables.I am going to use this approach for convenience.
     * <p>
     * But to further increase security, credentials should be encrypted and stored in good password manager
     * (inside the password database) and using password manager API to retrieve the credentials
     * from the database.
     */
    // Now lets retrieve credentials for ZOHO PROJECTS API, so we can use it.
    private static final String ZOHO_OAUTHTOKEN = System.getenv("ZOHO_OAUTHTOKEN");
    private static final String PORTAL_ID= System.getenv("PORTAL_ID");

    public static void main(String[] args) throws Exception {
        // App expects env variables (SLACK_BOT_TOKEN, SLACK_SIGNING_SECRET)
        App app = new App();

        //Trying to create and play with a custom command (e.g. say function to channel)
        app.command("/greetings", (req, ctx) -> ctx.ack(":wave: Hello!"));

        //Initialize timestamp
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd-uuuu");
        LocalDate localDate = LocalDate.now();
        String date =  dtf.format(localDate);

        /**
         * Start zoho API request (REST API)
         * In this case we are trying to connect to ZOHO PROJECT portal of particular company.
         * From there we want to retrieve all users (company employee) monthly timesheets.
         * To be more specific, we want to summarize work time of employees in the last month.
         * Or in other words how many hours each employee worked in the last month.
         * <p>
         * For more information on how to create this request, see ZOHO REST API documentation
         */

        URL url;
        HttpURLConnection request = null;
        String method = "GET";
        String parameters;
        String path;
        String toInsert = "";

        try {
            /*
             * Set the URL and Parameters to create connection
             * Set Request Method (GET, POST or DELETE)
             */
            if("GET".equals(method))
            {
                //variables below should not also be hardcoded
                parameters = "users_list=7656874152&view_type=month&date=" + date + "&bill_status=All&component_type=task";
                path = "/portal/" + PORTAL_ID + "/projects/1487937000000053229/logs/?";
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
            String toConcatenate = "Do Viet Anh" + "\n" + "Total hours:" + " " + grandTotal +  "\n" + "Billable hours:" + " " + billableHours + "\n" + "Non Billable hours:" + " " + nonBillableHours;

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

        // create a custom command that will send summarization of working hours to channel
        String finalToInsert = toInsert;
        app.command("/timelog", (req, ctx) -> ctx.ack(finalToInsert));

        // we can also for example use webhooks that will automatically send our data to a channel by
        // "listening" to a certain events. This could be quite practical in situation where we work with
        // cron jobs for example. (Use case: "At the end of every month cron ensures that webhook
        // sends a message to the channel and so on.")
        Slack slack = Slack.getInstance();

        // try to use Slack webhook to forward the message (summarization of working ours) to project/task channel
        String webhookUrl = "https://hooks.slack.com/services/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WMi";
        //String webhookUrl = System.getenv("SLACK_WEBHOOK_URL");
        Payload payload = Payload.builder().text(toInsert).build();

        WebhookResponse response = slack.send(webhookUrl, payload);
        System.out.println(response); // WebhookResponse(code=200, message=OK, body=ok)


        //initiate instance of Slack App server to start listen to events
        //it is possible to host our Slack integrations and apps on some cloud infrastructure
        //provided by various hosting providers (AWS, Google Cloud, MS Azure, ...)
        SlackAppServer server = new SlackAppServer(app);
        server.start(); // http://localhost:3000/slack/events
    }
}
