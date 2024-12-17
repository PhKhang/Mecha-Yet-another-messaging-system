package com.example.mechaadmin.bus;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import com.example.mechaadmin.dao.LogDAO;
import com.example.mechaadmin.dto.ActivityDTO;
import com.example.mechaadmin.dto.RecentLoginDTO;
import com.example.mechaadmin.dto.UsageDTO;

public class UsageBUS {
    public List<ActivityDTO> getAllActivity() {
        Configuration configuration = new Configuration();
        configuration.configure("hibernate.cfg.xml");

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        Session session = sessionFactory.openSession();

        session.beginTransaction();

        List<ActivityDTO> logs = session.createQuery("SELECT " + 
                        "    u.username, " + 
                        "    u.fullName, " + 
                        "    u.createdAt, " + 
                        "    COUNT(DISTINCT CASE WHEN c.chatType = 'private' THEN m.chatId END) AS distinct_private_chats, " + 
                        "    COUNT(DISTINCT CASE WHEN c.chatType = 'group' THEN m.chatId END) AS distinct_group_chats " + 
                        "FROM " + 
                        "    UserDAO u " + 
                        "left JOIN " + 
                        "    MessageDAO m ON u.userId = m.senderId " + 
                        "left JOIN " + 
                        "    ChatDAO c ON m.chatId = c.chatId " + 
                        "GROUP BY " + 
                        "    u.username;", ActivityDTO.class).getResultList();

        session.getTransaction().commit();

        return logs;
    }
    public List<RecentLoginDTO> getAllRecentLogin() {
        Configuration configuration = new Configuration();
        configuration.configure("hibernate.cfg.xml");

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        Session session = sessionFactory.openSession();

        session.beginTransaction();

        List<RecentLoginDTO> logs = session
                .createQuery("select l.sectionStart, u.username, u.fullName from LogDAO l " +
                        "join UserDAO u on l.userId = u.userId " +
                        "order by l.sectionStart desc", RecentLoginDTO.class)
                .getResultList();

        session.getTransaction().commit();

        return logs;
    }

    public List<UsageDTO> getAppOpened(int year) {
        Configuration configuration = new Configuration();
        configuration.configure("hibernate.cfg.xml");

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        Session session = sessionFactory.openSession();

        session.beginTransaction();

        List<Object[]> reports = session
                .createQuery(
                        "select month(l.sectionStart) as month, count(distinct l.userId) as opened, year(l.sectionStart) as year "
                                +
                                "from LogDAO l " +
                                "where year(l.sectionStart) = :year " +
                                "group by month(l.sectionStart), year(l.sectionStart)",
                        Object[].class)
                .setParameter("year", year)
                .getResultList();

        session.getTransaction().commit();

        List<UsageDTO> list = new ArrayList<UsageDTO>();

        for (Object[] report : reports) {
            UsageDTO usageDTO = new UsageDTO();
            usageDTO.setMonth((Integer) report[0]);
            usageDTO.setOpened(((Long) report[1]).intValue());
            usageDTO.setYear((Integer) report[2]);
            list.add(usageDTO);
        }

        return list;
    }
}