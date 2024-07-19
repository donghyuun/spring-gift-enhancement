package gift.domain.cartItem;

import gift.domain.product.JpaProductRepository;
import gift.domain.product.Product;
import gift.domain.user.JpaUserRepository;
import gift.domain.user.User;
import gift.global.exception.BusinessException;
import gift.global.exception.product.ProductNotFoundException;
import gift.global.exception.user.UserNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartItemService {

    private final JpaProductRepository productRepository;
    private final JpaCartItemRepository cartItemRepository;
    private final JpaUserRepository userRepository;

    public CartItemService(
        JpaProductRepository jpaProductRepository,
        JpaCartItemRepository jpaCartItemRepository,
        JpaUserRepository jpaUserRepository
    ) {
        this.userRepository = jpaUserRepository;
        this.cartItemRepository = jpaCartItemRepository;
        this.productRepository = jpaProductRepository;
    }

    /**
     * 장바구니에 상품 ID 추가
     */
    @Transactional
    public int addCartItem(Long userId, Long productId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // 기존에 존재하면 update
        Optional<CartItem> findCartItem = cartItemRepository.findByUserIdAndProductId(userId,
            productId);
        if (findCartItem.isPresent()) {
            return findCartItem.get().addOneMore();
        }
        // 기존에 없었으면 new
        CartItem newCartItem = new CartItem(user, product);
        cartItemRepository.save(newCartItem);
        return newCartItem.getCount();
    }

    /**
     * 장바구니 상품 조회 - 페이징(매개변수별)
     */
    public Page<Product> getProductsInCartByUserIdAndPageAndSort(Long userId, int page, int size,
        Sort sort) {
        List<Long> productIds = cartItemRepository.findAllByUserId(userId).stream()
            .map(cartItem -> cartItem.getProduct().getId())
            .collect(Collectors.toList());

        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Product> productsPage = productRepository.findAllByIdIn(productIds,
            pageRequest); // 영속성 컨텍스트에 이미 존재

        List<Product> products = productsPage.getContent().stream()
            .map(product -> {
                return Product.createProductFromProxy(product);
            })
            .toList();

        // 새 Page 객체 생성
        return PageableExecutionUtils.getPage(products, pageRequest,
            productsPage::getTotalElements);
    }

    /**
     * 장바구니 상품 삭제
     */
    public void deleteCartItem(Long userId, Long productId) {
        cartItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    /**
     * 장바구니 상품 수량 수정
     */
    @Transactional
    public int updateCartItem(Long userId, Long productId, int count) {
        CartItem findCartItem = cartItemRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "해당 상품이 존재하지 않습니다."));
        findCartItem.updateCount(count); // 수량 수정
        return count;
    }
}
