package com.hackathon.inditex.service.impl;

import com.hackathon.inditex.Commons.dtos.request.CreateOrderRequest;
import com.hackathon.inditex.Commons.dtos.response.OrderCreatedResponse;
import com.hackathon.inditex.Commons.dtos.response.OrderResponse;
import com.hackathon.inditex.Commons.dtos.response.ProcessedOrder.ProcessedOrderResponseAssigned;
import com.hackathon.inditex.Commons.dtos.response.ProcessedOrder.ProcessedOrderResponsePending;
import com.hackathon.inditex.Controllers.Exception.CustomException;
import com.hackathon.inditex.Entities.Center;
import com.hackathon.inditex.Entities.Coordinates;
import com.hackathon.inditex.Entities.Order;
import com.hackathon.inditex.Repositories.CenterRepository;
import com.hackathon.inditex.Repositories.OrderRepository;
import com.hackathon.inditex.service.CenterService;
import com.hackathon.inditex.service.OrderService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CenterRepository centerRepository;
    private final CenterService centerService;

    public OrderServiceImpl(OrderRepository orderRepository, CenterRepository centerRepository, CenterService centerService) {
        this.orderRepository = orderRepository;
        this.centerRepository = centerRepository;
        this.centerService = centerService;
    }

    @Override
    public OrderCreatedResponse createOrder(CreateOrderRequest request) {

        Order order = new Order();

        order.setCustomerId(request.getCustomerId());
        order.setSize(String.valueOf(request.getSize()));
        order.setStatus("PENDING");
        order.setAssignedCenter(null);
        order.setCoordinates(request.getCoordinates());

        orderRepository.save(order);

        OrderCreatedResponse orderCreatedResponse = OrderCreatedResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .size(request.getSize())
                .assignedLogisticsCenter(order.getAssignedCenter())
                .coordinates(order.getCoordinates())
                .status(order.getStatus())
                .message("Order created successfully in PENDING status.")
                .build();

        return orderCreatedResponse;
    }

    @Override
    public List<OrderResponse> getAllOrders() {

        List<Order> orders = orderRepository.findAll();

        List<OrderResponse> orderResponses = new ArrayList<>();

        for(Order order : orders) {

            orderResponses.add(OrderResponse.builder()
                            .id(order.getId())
                            .customerId(order.getCustomerId())
                            .size(order.getSize())
                            .status(order.getStatus())
                            .assignedCenter(order.getAssignedCenter())
                            .coordinates(order.getCoordinates())
                    .build());

        }

        return orderResponses;
    }

    @Override
    public Map<String,LinkedList<Object>> centerAssignment() {

        Map<String,LinkedList<Object>> processedOrdersResponse = new LinkedHashMap<>();

        LinkedList<Object> processedOrders = new LinkedList<>();

        List<Order> ordersPending =  orderRepository.findByStatus("PENDING");

        List<Center> centersAvailable = centerRepository.findByStatus("AVAILABLE");

        for(Order order : ordersPending) {

            List<Center> centersSupportOrderType = new ArrayList<>();

            for(Center center : centersAvailable) {
                if(center.getCapacity().contains((CharSequence) order.getSize())){
                    centersSupportOrderType.add(center);
                }
            }

            //No hay centros que soporten este tipo de Orden
            // "message": "No available centers support the order type.",
            if(centersSupportOrderType.isEmpty()) {
                processedOrders.add(
                        ProcessedOrderResponsePending.builder()
                                .distance(null)
                                .orderId(order.getId())
                                .assignedLogisticsCenter(null)
                                .message("No available centers support the order type.")
                                .status(order.getStatus())
                                .build()
                );
            }
            else{

                List<Center> centerAssignedByMaxCapacity = new ArrayList<>();

                for(Center center : centersSupportOrderType) {
                    if(center.getCurrentLoad()<center.getMaxCapacity()){
                        centerAssignedByMaxCapacity.add(center);
                    }
                }

                //Los centros soportan el tipo de orden pero no tienen espacio disponible
                // "message": "All centers are at maximum capacity.",
                if(centerAssignedByMaxCapacity.isEmpty()) {

                    processedOrders.add(
                            ProcessedOrderResponsePending.builder()
                                    .distance(null)
                                    .orderId(order.getId())
                                    .assignedLogisticsCenter(null)
                                    .message("All centers are at maximum capacity.")
                                    .status(order.getStatus())
                                    .build()
                    );

                } else {  //Centros disponibles, buscará el que menos distancia tenga

                    Map<Center, Double> closesCenterAndDistance = calculateClosestCenterAvailable(order, centerAssignedByMaxCapacity);
                    Map.Entry<Center, Double> entry = closesCenterAndDistance.entrySet().iterator().next();
                    Center closestCenter = entry.getKey();
                    Double distance = entry.getValue();

                    closestCenter.setCurrentLoad( closestCenter.getCurrentLoad() + 1 );
                    centerRepository.save(closestCenter);

                    order.setStatus("ASSIGNED");
                    order.setAssignedCenter(closestCenter.getName());
                    orderRepository.save(order);

                    processedOrders.add(
                            ProcessedOrderResponseAssigned.builder()
                                    .distance(distance)
                                    .orderId(order.getId())
                                    .assignedLogisticsCenter(order.getAssignedCenter())
                                    .status(order.getStatus())
                                    .build()
                    );

                }
            }
        }

        processedOrdersResponse.put("processed-orders", processedOrders);
        return processedOrdersResponse;

    }

    private Map<Center,Double> calculateClosestCenterAvailable(Order order, List<Center> centersAvailable) {

        Map<Center,Double> closesCenterAndDistance = new HashMap<>();
        double distance = Double.MAX_VALUE;
        Center closestCenter = null;

       for(Center center : centersAvailable) {
            if(calculateDistance(order.getCoordinates(),center.getCoordinates()) < distance) {
                closestCenter = center;
                distance = calculateDistance(order.getCoordinates(),center.getCoordinates());
            }
       }

        closesCenterAndDistance.put(closestCenter,distance);

       return closesCenterAndDistance;

    }
    //Calculate the distance between two Coordinates using haversine formula
    public double calculateDistance(Coordinates orderCoordinates , Coordinates centerCoordinates) {

        final BigDecimal R = new BigDecimal("6371000.00000001");  // Radio de la Tierra en metros (6371 km * 1000)

        BigDecimal lat1 = BigDecimal.valueOf(Math.toRadians(orderCoordinates.getLatitude()));
        BigDecimal lon1 = BigDecimal.valueOf(Math.toRadians(orderCoordinates.getLongitude()));
        BigDecimal lat2 = BigDecimal.valueOf(Math.toRadians(centerCoordinates.getLatitude()));
        BigDecimal lon2 = BigDecimal.valueOf(Math.toRadians(centerCoordinates.getLongitude()));

        BigDecimal deltaLat = lat2.subtract(lat1);
        BigDecimal deltaLon = lon2.subtract(lon1);

        BigDecimal a = BigDecimal.valueOf(Math.pow(Math.sin(deltaLat.doubleValue() / 2), 2))
                .add(BigDecimal.valueOf(Math.cos(lat1.doubleValue()))
                        .multiply(BigDecimal.valueOf(Math.cos(lat2.doubleValue()))
                                .multiply(BigDecimal.valueOf(Math.pow(Math.sin(deltaLon.doubleValue() / 2), 2)))));

        BigDecimal c = BigDecimal.valueOf(2 * Math.atan2(Math.sqrt(a.doubleValue()), Math.sqrt(1 - a.doubleValue())));

        // Distancia final en metros
        BigDecimal distance = R.multiply(c).setScale(15, RoundingMode.HALF_UP);

        return distance.doubleValue()/1000;
    }

}
