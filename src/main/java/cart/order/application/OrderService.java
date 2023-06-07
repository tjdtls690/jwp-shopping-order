package cart.order.application;

import cart.cartitem.domain.CartItem;
import cart.cartitem.repository.CartItemRepository;
import cart.member.domain.Member;
import cart.member.repository.MemberRepository;
import cart.order.domain.Order;
import cart.order.domain.OrderInfo;
import cart.order.dto.OrderDetailResponse;
import cart.order.dto.OrderInfoResponse;
import cart.order.dto.OrderRequest;
import cart.order.dto.OrderResponse;
import cart.order.repository.OrderRepository;
import cart.product.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final CartItemRepository cartItemRepository;
    
    public OrderService(final OrderRepository orderRepository, final MemberRepository memberRepository, final CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
        this.cartItemRepository = cartItemRepository;
    }
    
    public Long order(final Member member, final OrderRequest orderRequest) {
        final Member memberByEmail = memberRepository.getMemberByEmail(member.getEmail());
        final List<CartItem> cartItems = cartItemRepository.findAllByIds(getCartItemIds(orderRequest));
        validateOwnerOfCartItem(memberByEmail, cartItems);
        removeCartItems(orderRequest);
        
        final Order order =
                new Order(getOrderInfos(cartItems), orderRequest.getOriginalPrice(), orderRequest.getUsedPoint(), orderRequest.getPointToAdd());
        
        memberByEmail.usePoint(orderRequest.getUsedPoint());
        memberByEmail.accumulatePoint(orderRequest.getPointToAdd());
        
        memberRepository.update(memberByEmail);
        return orderRepository.save(memberByEmail.getId(), order);
    }
    
    private List<Long> getCartItemIds(final OrderRequest orderRequest) {
        return orderRequest.getCartItemIds().stream()
                .collect(Collectors.toUnmodifiableList());
    }
    
    private void validateOwnerOfCartItem(final Member member, final List<CartItem> cartItems) {
        cartItems.forEach(cartItem -> cartItem.checkOwner(member));
    }
    
    private List<OrderInfo> getOrderInfos(final List<CartItem> cartItems) {
        return cartItems.stream()
                .map(cartItem -> {
                    final Product product = cartItem.getProduct();
                    return new OrderInfo(product, product.getName(), product.getPrice(), product.getImageUrl(), cartItem.getQuantity());
                })
                .collect(Collectors.toUnmodifiableList());
    }
    
    private void removeCartItems(final OrderRequest orderRequest) {
        orderRequest.getCartItemIds()
                .forEach(cartItemRepository::removeById);
    }
    
    @Transactional(readOnly = true)
    public List<OrderResponse> findByMember(final Member member) {
        return orderRepository.findByMember(member).stream()
                .map(order -> new OrderResponse(order.getId(), getOrderInfoResponses(order)))
                .collect(Collectors.toUnmodifiableList());
    }
    
    private List<OrderInfoResponse> getOrderInfoResponses(final Order order) {
        return order.getOrderInfos().getOrderInfos().stream()
                .map(OrderInfoResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }
    
    @Transactional(readOnly = true)
    public OrderDetailResponse findByMemberAndId(final Member member, final Long orderId) {
        final Order order = orderRepository.findByMemberAndId(member, orderId);
        return OrderDetailResponse.from(order);
    }
}