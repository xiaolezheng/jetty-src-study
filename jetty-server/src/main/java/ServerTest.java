import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;

/**
 * Created by xiaolezheng on 17/6/15.
 */
public class ServerTest {
    public static void main(String[] args) throws Exception{
        Server server = new Server(8080);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setResourceBase("./");

        server.setHandler(resourceHandler);

        server.start();
    }
}
