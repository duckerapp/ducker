#include "bridge_helper.h"
#include <assert.h>

int main(void) {
    const uint64_t kb = 1024u, mb = kb * kb, gb = mb * kb;
    assert(down_scale_traffic(kb - 1) == kb - 1);
    assert(down_scale_traffic(kb) == ((1u << 30) | 100u));
    assert(down_scale_traffic(mb - 1) >> 30 == 1u);
    assert(down_scale_traffic(mb) == ((2u << 30) | 100u));
    assert(down_scale_traffic(gb - 1) >> 30 == 2u);
    assert(down_scale_traffic(gb) == ((3u << 30) | 100u));
    assert(down_scale_traffic(1030u * mb) >> 30 == 3u);
    assert(down_scale_traffic(2u * gb) == ((3u << 30) | 200u));
    return 0;
}
