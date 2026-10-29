import React, { useState } from 'react';
import { Store, X, Star, MapPin, Phone, Mail, ShoppingBag, Check } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

export const ShopDetailModal: React.FC = () => {
  const { selectedShop, closeModal } = useVybe();
  const [orderConfirmedItem, setOrderConfirmedItem] = useState<string | null>(null);

  if (!selectedShop) return null;

  const handleOrder = (itemName: string) => {
    setOrderConfirmedItem(itemName);
    setTimeout(() => setOrderConfirmedItem(null), 3000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl overflow-hidden text-white no-scrollbar">
        
        {/* Cover */}
        <div className="h-44 w-full relative">
          <img 
            src={selectedShop.coverUrl} 
            alt={selectedShop.businessName}
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-[#0a0f1d] via-transparent to-transparent" />
          <button 
            onClick={closeModal}
            className="absolute top-4 right-4 p-2 rounded-full bg-black/60 text-white hover:bg-black/80"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 -mt-12 relative space-y-5">
          <div className="flex items-center gap-4">
            <img 
              src={selectedShop.logoUrl} 
              alt={selectedShop.businessName}
              className="w-20 h-20 rounded-2xl object-cover border-4 border-[#0a0f1d] shadow-xl"
            />
            <div>
              <div className="flex items-center gap-1.5">
                <h2 className="text-xl font-bold brand-font text-white">{selectedShop.businessName}</h2>
                <OfficialBadge badge="SHOP_GOLD" size="md" />
              </div>
              <p className="text-xs text-[#00C6FF] font-semibold">{selectedShop.category}</p>
              <div className="flex items-center gap-1.5 mt-1 text-xs text-amber-400">
                <Star className="w-3.5 h-3.5 fill-current" />
                <span className="font-bold">{selectedShop.rating}</span>
                <span className="text-gray-400">({selectedShop.reviewsCount} customer reviews)</span>
              </div>
            </div>
          </div>

          <p className="text-xs text-gray-300 leading-relaxed">
            {selectedShop.description}
          </p>

          {/* Shop Meta */}
          <div className="p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] text-xs text-gray-300 space-y-1.5">
            <div className="flex items-center gap-2">
              <MapPin className="w-3.5 h-3.5 text-[#FFB703]" />
              <span>{selectedShop.locationAddress}</span>
            </div>
            <div className="flex items-center gap-4 text-gray-400 text-[11px] pt-1">
              <span>🕒 {selectedShop.openingHours}</span>
              <span>📞 {selectedShop.contactPhone}</span>
            </div>
          </div>

          {/* Order confirmation toast */}
          {orderConfirmedItem && (
            <div className="p-3 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-200 text-xs flex items-center gap-2">
              <Check className="w-4 h-4 text-emerald-400" />
              <span>Order request for <strong>{orderConfirmedItem}</strong> sent directly to {selectedShop.businessName}!</span>
            </div>
          )}

          {/* Catalog items */}
          <div className="space-y-3">
            <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider flex items-center gap-1.5">
              <ShoppingBag className="w-4 h-4 text-[#00C6FF]" />
              Featured Artisan Catalog
            </h3>
            <div className="space-y-2.5">
              {selectedShop.catalog.map(item => (
                <div 
                  key={item.id}
                  className="p-3 rounded-xl bg-[#101322] border border-[#262E4A] flex items-center justify-between gap-3"
                >
                  <img 
                    src={item.imageUrl} 
                    alt={item.name}
                    className="w-14 h-14 rounded-xl object-cover shrink-0" 
                  />
                  <div className="flex-1">
                    <h4 className="text-xs font-bold text-white">{item.name}</h4>
                    <p className="text-[11px] text-gray-400 line-clamp-1">{item.description}</p>
                    <span className="text-xs font-bold text-[#FFB703] mt-0.5 block">{item.priceFormatted}</span>
                  </div>
                  <button
                    onClick={() => handleOrder(item.name)}
                    className="px-3 py-1.5 bg-[#8B5CF6] hover:bg-[#7C3AED] text-white text-xs font-bold rounded-lg shadow shrink-0"
                  >
                    Request
                  </button>
                </div>
              ))}
            </div>
          </div>

        </div>

      </div>
    </div>
  );
};
