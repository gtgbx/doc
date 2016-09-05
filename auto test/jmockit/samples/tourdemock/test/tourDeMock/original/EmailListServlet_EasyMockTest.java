package tourDeMock.original;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import static java.util.Arrays.*;
import static org.easymock.EasyMock.*;
import org.easymock.*;
import org.junit.*;
import tourDeMock.original.service.*;

public final class EmailListServlet_EasyMockTest extends EasyMockSupport
{
   EmailListServlet servlet;

   HttpServletRequest request;
   HttpServletResponse response;
   EmailListService emailListService;

   @Before
   public void before() throws Exception
   {
      request = createNiceMock(HttpServletRequest.class);
      response = createNiceMock(HttpServletResponse.class);
      emailListService = createMock(EmailListService.class);

      ServletConfig servletConfig = createNiceMock(ServletConfig.class);
      ServletContext servletContext = createNiceMock(ServletContext.class);

      expect(servletConfig.getServletContext()).andReturn(servletContext);
      expect(servletContext.getAttribute(EmailListService.KEY)).andReturn(emailListService);
      replay(servletConfig, servletContext);

      servlet = new EmailListServlet();
      servlet.init(servletConfig);
   }

   @Test(expected = ServletException.class)
   public void doGetWithoutList() throws Exception
   {
      expect(emailListService.getListByName(null)).andThrow(new EmailListNotFound());
      replay(request, emailListService);

      servlet.doGet(request, response);
   }

   @Test
   public void doGetWithList() throws Exception
   {
      List<String> emails = asList("larry@stooge.com", "moe@stooge.com", "curley@stooge.com");
      expect(emailListService.getListByName((String) anyObject())).andReturn(emails);

      PrintWriter writer = createStrictMock(PrintWriter.class);
      expect(response.getWriter()).andReturn(writer);

      writer.println("larry@stooge.com");
      writer.println("moe@stooge.com");
      writer.println("curley@stooge.com");
      response.flushBuffer();

      replay(request, response, writer, emailListService);

      servlet.doGet(request, response);

      verifyAll();
   }
}
