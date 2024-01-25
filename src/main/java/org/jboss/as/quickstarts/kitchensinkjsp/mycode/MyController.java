package org.jboss.as.quickstarts.kitchensinkjsp.mycode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.sql.DataSource;

@RequestScoped
public class MyController {

    @Resource(lookup = "java:jboss/datasources/KitchensinkJSPQuickstartDS")
    DataSource dataSource;

    @Produces
    @Named
    public List<MyMember> getMyList() {

        List<MyMember> members = new ArrayList<MyMember>();

        try {
            for (Map<String, String> m : accessDatabase()) {

                MyMember mem = new MyMember(
                    m.get("name"),
                    m.get("email"),
                    m.get("phone_number")
                );
                members.add(mem);

            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return members;
    }

    private List<Map<String, String>> accessDatabase() throws Exception {
        List<Map<String, String>> list = new ArrayList<>();

        try (Connection con = dataSource.getConnection()) {
            Statement s = con.createStatement();
            ResultSet rs = s.executeQuery("SELECT UA1010, UA1A90, UA1230 FROM UA1P WHERE UA1010='B1644'");
            while (rs.next()) {
                Map<String, String> row = new HashMap<String, String>();
                row.put("name", rs.getString(1));
                row.put("email", rs.getString(2));
                row.put("phone_number", rs.getString(3));
                list.add(row);
            }
        }

        return list;
    }

}
