package tourDeMock.original;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import static java.util.Arrays.*;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import static org.mockito.Mockito.*;
import org.mockito.runners.*;
import tourDeMock.original.service.*;

@RunWith(MockitoJUnitRunner.class)
public final class EmailListServlet_MockitoTest
{
   EmailListServlet servlet;

   @Mock HttpServletRequest request;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS) HttpServletResponse response;
   @Mock EmailListService service;

   @Mock(answer = Answers.RETURNS_DEEP_STUBS) ServletConfig servletConfig;

   @Before
   public void before() throws Exception
   {
      when(servletConfig.getServletContext().getAttribute(EmailListService.KEY)).thenReturn(service);

      servlet = new EmailListServlet();
      servlet.init(servletConfig);
   }

   @Test(expected = ServletException.class)
   public void doGetWithoutList() throws Exception
   {
      when(service.getListByName(null)).thenThrow(new EmailListNotFound());

      servlet.doGet(request, response);
   }

   @Test
   public void doGetWithList() throws Exception
   {
      PrintWriter writer = response.getWriter();

      List<String> emails = asList("larry@stooge.com", "moe@stooge.com", "curley@stooge.com");
      when(service.getListByName(anyString())).thenReturn(emails);

      servlet.doGet(request, response);

      InOrder order = inOrder(writer, response);
      order.verify(writer).println("larry@stooge.com");
      order.verify(writer).println("moe@stooge.com");
      order.verify(writer).println("curley@stooge.com");
      order.verify(response).flushBuffer();
   }
}
